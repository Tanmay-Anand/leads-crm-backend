package com.leadrat.crm.leads.api.rbac;

import com.leadrat.crm.leads.api.auth.PermissionsChangedEvent;
import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.rbac.dto.RoleRequest;
import com.leadrat.crm.leads.api.rbac.dto.RoleResponse;
import com.leadrat.crm.leads.api.seeding.TenantSeedingService;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import com.leadrat.crm.leads.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PermissionValidator permissionValidator;
    private final TenantAware tenantAware;
    private final TenantSeedingService seedingService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PermissionCatalogResponse getCatalog() {
        PermissionCatalogResponse response = new PermissionCatalogResponse();

        Set<String> actions = new LinkedHashSet<>();
        CrmPermission.CATALOG.values().forEach(actions::addAll);
        response.setActions(new ArrayList<>(actions));

        List<PermissionCatalogResponse.ResourceEntry> resources = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : CrmPermission.CATALOG.entrySet()) {
            PermissionCatalogResponse.ResourceEntry resourceEntry = new PermissionCatalogResponse.ResourceEntry();
            resourceEntry.setKey(entry.getKey());
            resourceEntry.setActions(entry.getValue());
            resourceEntry.setChildren(childrenOf(entry.getKey()));
            resources.add(resourceEntry);
        }
        response.setResources(resources);
        return response;
    }

    /** Sibling keys exactly one segment deeper than {@code key}, e.g. "master-data" ->
     *  ["master-data/lead-statuses", "master-data/tags", ...]. */
    private List<String> childrenOf(String key) {
        String prefix = key + "/";
        List<String> children = new ArrayList<>();
        for (String candidate : CrmPermission.CATALOG.keySet()) {
            if (candidate.startsWith(prefix) && !candidate.substring(prefix.length()).contains("/")) {
                children.add(candidate);
            }
        }
        return children;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        UUID tenantId = requireTenant();
        seedingService.ensureSeeded(tenantId);
        return roleRepository.findByTenant(tenantId).stream()
                .map(role -> RoleResponse.fromEntity(role, userRepository.countByTenantAndCustomRoleIdAndIsActiveTrue(tenantId, role.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID id) {
        UUID tenantId = requireTenant();
        Role role = requireRole(id);
        return RoleResponse.fromEntity(role, userRepository.countByTenantAndCustomRoleIdAndIsActiveTrue(tenantId, role.getId()));
    }

    @Override
    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        UUID tenantId = requireTenant();
        permissionValidator.validate(request.permissions());

        if (roleRepository.existsByTenantAndNameIgnoreCase(tenantId, request.name())) {
            throw new LeadratException("A role named " + request.name() + " already exists.", HttpStatus.CONFLICT);
        }

        Role role = request.toEntity();
        role.setPermissions(dedupe(request.permissions()));
        role.tenant(tenantId);
        Role saved = roleRepository.save(role);
        log.info("Created role {} for tenant {}", saved.getId(), tenantId);
        return RoleResponse.fromEntity(saved, 0);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(UUID id, RoleRequest request) {
        UUID tenantId = requireTenant();
        Role role = requireRole(id);

        if (role.isSystem()) {
            throw new LeadratException(
                    "System role '" + role.getName() + "' is immutable and cannot be updated.", HttpStatus.FORBIDDEN);
        }

        permissionValidator.validate(request.permissions());

        if (!role.getName().equalsIgnoreCase(request.name())
                && roleRepository.existsByTenantAndNameIgnoreCaseAndIdNot(tenantId, request.name(), id)) {
            throw new LeadratException("A role named " + request.name() + " already exists.", HttpStatus.CONFLICT);
        }

        role.setName(request.name());
        role.setPermissions(dedupe(request.permissions()));
        Role saved = roleRepository.save(role);
        // Every holder of this role sees the new permission set on their next request, not
        // after the cache's own 60s TTL - published after commit, see PermissionCacheEvictionListener.
        eventPublisher.publishEvent(PermissionsChangedEvent.forTenant(tenantId));
        return RoleResponse.fromEntity(saved, userRepository.countByTenantAndCustomRoleIdAndIsActiveTrue(tenantId, saved.getId()));
    }

    @Override
    @Transactional
    public void deleteRole(UUID id) {
        UUID tenantId = requireTenant();
        Role role = requireRole(id);

        if (role.isSystem()) {
            throw new LeadratException(
                    "System role '" + role.getName() + "' is immutable and cannot be deleted.", HttpStatus.FORBIDDEN);
        }

        role.softDelete();
        roleRepository.save(role);
        eventPublisher.publishEvent(PermissionsChangedEvent.forTenant(tenantId));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private List<String> dedupe(List<String> permissions) {
        return new ArrayList<>(new LinkedHashSet<>(permissions));
    }

    private Role requireRole(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
        tenantAware.validate(role);
        return role;
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
