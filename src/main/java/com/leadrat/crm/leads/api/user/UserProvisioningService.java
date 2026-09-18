package com.leadrat.crm.leads.api.user;

import com.leadrat.crm.leads.api.auth.Authorities;
import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Just-in-time user provisioning: the first time a Cognito-authenticated caller with no local
 * {@link User} row is seen, this creates one from their JWT claims.
 *
 * <p>The bootstrap problem this exists to dissolve: because {@code PermissionService}'s Layer 1
 * (the JWT group) is always the ceiling, and a {@link User} row only ever narrows it via a
 * custom role, a Cognito-only {@code TENANT_ADMIN} is already fully authorized before any row
 * exists - this service only needs to run for {@code /me} and the two listing endpoints that
 * display a name, never for an authorization decision itself.
 *
 * <p>Never called from {@code PermissionService} (an authorization check must not write) and
 * never from a filter - only from {@code UserServiceImpl.getMe()}/{@code getAll()}/{@code
 * getNames()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final TenantAware tenantAware;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureCurrentUserProvisioned() {
        UUID sub = tenantAware.getLoggedInUserId();
        UUID tenantId = tenantAware.getTenantId();
        if (sub == null || tenantId == null) {
            return;
        }

        Jwt jwt = currentJwt();
        if (jwt == null) {
            return;
        }

        UserRole jwtRole = Authorities.resolve(SecurityContextHolder.getContext().getAuthentication())
                .orElse(null);
        if (jwtRole == null) {
            log.warn("No recognizable role authority for {} - skipping provisioning", sub);
            return;
        }

        // The native finder bypasses @SQLRestriction: a deliberately deactivated account (soft
        // deleted) must still be found here, so this method can leave it alone rather than the
        // absence looking like "never provisioned" and silently recreating it active.
        Optional<User> existing = userRepository.findAnyById(sub);

        if (existing.isEmpty()) {
            User user = new User(sub);
            user.tenant(tenantId);
            user.setEmail(jwt.getClaimAsString("email"));
            user.setFirstName(jwt.getClaimAsString("given_name"));
            user.setLastName(jwt.getClaimAsString("family_name"));
            user.setRole(jwtRole);
            userRepository.save(user);
            log.info("Just-in-time provisioned user {} ({}) for tenant {}", sub, user.getEmail(), tenantId);
            return;
        }

        User user = existing.get();
        if (!user.isActive()) {
            // Deliberately deactivated (soft-deleted) - never resurrected by a login.
            return;
        }
        if (user.getRole() != jwtRole) {
            log.info("Correcting divergent role for {}: {} -> {}", sub, user.getRole(), jwtRole);
            user.setRole(jwtRole);
            userRepository.save(user);
        }
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
