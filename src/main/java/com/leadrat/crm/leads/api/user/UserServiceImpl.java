package com.leadrat.crm.leads.api.user;

import com.leadrat.crm.leads.api.auth.Authorities;
import com.leadrat.crm.leads.api.auth.PermissionService;
import com.leadrat.crm.leads.api.cognito.CognitoService;
import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.rbac.CrmPermission;
import com.leadrat.crm.leads.api.rbac.RoleRepository;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import com.leadrat.crm.leads.api.user.dto.MeResponse;
import com.leadrat.crm.leads.api.user.dto.UserDto;
import com.leadrat.crm.leads.api.user.dto.UserNamesDto;
import com.leadrat.crm.leads.api.user.dto.UserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantAware tenantAware;
    private final CognitoService cognitoService;
    private final UserProvisioningService provisioningService;
    private final PermissionService permissionService;

    @Override
    @Transactional(readOnly = true)
    public Page<UserDto> getAll(Pageable pageable, UserListFilters filters) {
        UUID tenantId = requireTenant();
        provisioningService.ensureCurrentUserProvisioned();
        if (filters.isEmpty()) {
            return userRepository.findByTenant(tenantId, pageable).map(this::toDto);
        }
        return userRepository.findAll(UserFilterSpecifications.matching(filters), pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDto> simpleSearch(String query, List<UserSearchField> searchFields, Pageable pageable,
                                       UserListFilters filters) {
        requireTenant();
        provisioningService.ensureCurrentUserProvisioned();
        Specification<User> spec = new UserSimpleSearchSpecification(query, searchFields);
        if (!filters.isEmpty()) {
            spec = spec.and(UserFilterSpecifications.matching(filters));
        }
        return userRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserNamesDto> getNames() {
        UUID tenantId = requireTenant();
        provisioningService.ensureCurrentUserProvisioned();
        return userRepository.findByTenantAndIsActiveTrueOrderByFirstNameAscLastNameAsc(tenantId).stream()
                .map(u -> new UserNamesDto(u.getId(), u.getDisplayName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return toDto(requireUser(id));
    }

    @Override
    @Transactional
    public UserDto create(UserRequest request) {
        UUID tenantId = requireTenant();

        if (isPlatformRole(request.role()) && !isCallerPlatformAdmin()) {
            throw new LeadratException("Only a platform admin may assign a platform role.", HttpStatus.FORBIDDEN);
        }

        userRepository.findAnyByTenantAndEmail(tenantId, request.email()).ifPresent(existing -> {
            if (existing.isActive()) {
                throw new LeadratException("A user with this email already exists.", HttpStatus.CONFLICT);
            }
        });

        if (request.customRoleId() != null) {
            requireRoleInTenant(request.customRoleId(), tenantId);
        }

        // Create order inverts relative to every other module here: the Cognito sub becomes
        // this row's primary key, so the AWS calls must happen first and stay outside any
        // transaction - a rollback must never leave an orphaned Cognito identity with no
        // matching local row silently un-rolled-back on the AWS side.
        UUID sub = cognitoService.createUser(request.email(), request.password(), request.role());

        Optional<User> reactivatable = userRepository.findAnyById(sub);
        User user = reactivatable.map(existing -> {
            if (existing.isActive()) {
                // The persist trap: an existing, active row for this exact sub with a different
                // email would mean Cognito adopted a stale identity - treat as a conflict rather
                // than silently overwriting someone else's account.
                throw new LeadratException("A user with this identity already exists.", HttpStatus.CONFLICT);
            }
            existing.setActive(true);
            return existing;
        }).orElseGet(() -> new User(sub));

        user.tenant(tenantId);
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        user.setCustomRoleId(request.customRoleId());
        user.setEnabled(true);

        User saved = userRepository.save(user);
        log.info("Created user {} ({}) for tenant {}", saved.getId(), saved.getEmail(), tenantId);
        return toDto(saved);
    }

    @Override
    @Transactional
    public UserDto edit(UUID id, UserDto request) {
        UUID tenantId = requireTenant();
        User user = requireUser(id);
        boolean isSelf = isSelf(user);

        if (isSelf && request.role() != user.getRole()) {
            throw new LeadratException("You cannot change your own role.", HttpStatus.FORBIDDEN);
        }

        if (isPlatformRole(request.role()) && !isCallerPlatformAdmin()) {
            throw new LeadratException("Only a platform admin may assign a platform role.", HttpStatus.FORBIDDEN);
        }

        if (user.getRole() == UserRole.TENANT_ADMIN && request.role() != UserRole.TENANT_ADMIN
                && countActiveAdmins(tenantId) <= 1) {
            throw new LeadratException("Cannot demote the last active admin.", HttpStatus.CONFLICT);
        }

        if (request.customRoleId() != null) {
            requireRoleInTenant(request.customRoleId(), tenantId);
        }

        request.applyTo(user);
        User saved = userRepository.save(user);
        // Role/custom-role changes narrow or widen what this user can do right now, not after
        // the cache's own 60s TTL.
        permissionService.evictUser(tenantId, id);
        return toDto(saved);
    }

    @Override
    @Transactional
    public UserDto setStatus(UUID id, boolean enabled) {
        UUID tenantId = requireTenant();
        User user = requireUser(id);

        if (isSelf(user)) {
            throw new LeadratException("You cannot change your own status.", HttpStatus.FORBIDDEN);
        }
        if (user.getRole() == UserRole.TENANT_ADMIN || user.getRole() == UserRole.PLATFORM_ADMIN) {
            throw new LeadratException("An admin's status cannot be changed.", HttpStatus.FORBIDDEN);
        }

        user.setEnabled(enabled);
        User saved = userRepository.save(user);
        cognitoService.setEnabled(user.getEmail(), enabled);
        // A status change narrows or widens what this user can do right now, not in 60s.
        permissionService.evictUser(tenantId, id);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public void resetPassword(UUID id, String newPassword) {
        User user = requireUser(id);
        cognitoService.resetPassword(user.getEmail(), newPassword);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = requireTenant();
        User user = requireUser(id);

        if (isSelf(user)) {
            throw new LeadratException("You cannot delete yourself.", HttpStatus.FORBIDDEN);
        }
        if (user.getRole() == UserRole.TENANT_ADMIN && countActiveAdmins(tenantId) <= 1) {
            throw new LeadratException("Cannot delete the last active admin.", HttpStatus.CONFLICT);
        }

        user.softDelete();
        userRepository.save(user);
        // AdminDisableUser, not AdminDeleteUser - a hard delete frees the email for a new sub
        // while leads still point at the old one (Lead.assignedTo has no FK to enforce this).
        cognitoService.setEnabled(user.getEmail(), false);
    }

    @Override
    @Transactional
    public MeResponse getMe() {
        UUID tenantId = requireTenant();
        UUID userId = tenantAware.getLoggedInUserId();
        provisioningService.ensureCurrentUserProvisioned();

        User user = userRepository.findAnyById(userId).orElse(null);
        UserRole role = user != null ? user.getRole()
                : Authorities.resolve(SecurityContextHolder.getContext().getAuthentication()).orElse(null);

        boolean unrestricted = permissionService.isAdmin(SecurityContextHolder.getContext().getAuthentication());
        List<String> permissions = unrestricted
                ? List.copyOf(CrmPermission.ALL)
                : permissionService.effectivePermissions(tenantId, userId);

        return new MeResponse(
                userId,
                tenantId,
                user != null ? user.getEmail() : null,
                user != null ? user.getDisplayName() : tenantAware.getLoggedInUsername(),
                role,
                unrestricted,
                permissions);
    }

    // ─── business guards ──────────────────────────────────────────────────────

    private boolean isSelf(User user) {
        UUID currentId = tenantAware.getLoggedInUserId();
        return currentId != null && currentId.equals(user.getId());
    }

    private boolean isPlatformRole(UserRole role) {
        return role == UserRole.PLATFORM_ADMIN || role == UserRole.PLATFORM_USER;
    }

    private boolean isCallerPlatformAdmin() {
        return Authorities.has(SecurityContextHolder.getContext().getAuthentication(), UserRole.PLATFORM_ADMIN);
    }

    private long countActiveAdmins(UUID tenantId) {
        return userRepository.countByTenantAndRoleAndEnabledTrueAndIsActiveTrue(tenantId, UserRole.TENANT_ADMIN);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private UserDto toDto(User user) {
        return UserDto.fromEntity(user);
    }

    private User requireUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        tenantAware.validate(user);
        return user;
    }

    private void requireRoleInTenant(UUID roleId, UUID tenantId) {
        roleRepository.findById(roleId)
                .filter(role -> tenantId.equals(role.getTenant()))
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));
    }

    private UUID requireTenant() {
        UUID tenantId = tenantAware.getTenantId();
        if (tenantId == null) {
            throw new LeadratException("Tenant context is required for this operation",
                    HttpStatus.PRECONDITION_FAILED);
        }
        return tenantId;
    }
}
