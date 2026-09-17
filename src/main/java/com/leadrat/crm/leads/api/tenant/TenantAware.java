package com.leadrat.crm.leads.api.tenant;

import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.util.SystemToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * The one place that answers "who is calling, and for which tenant".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAware {

    /**
     * Resolves the current tenant ID from the security context, in order:
     * <ol>
     *   <li>{@link SystemToken} — internal system operations</li>
     *   <li>{@link TenantContext} — set by {@link TenantFilter} from the {@code x-tenant-id} header</li>
     *   <li>JWT claims — {@code custom:tenantId}, then legacy aliases</li>
     * </ol>
     *
     * @return the tenant ID, or null if not resolvable
     */
    public UUID getTenantId() {

        if (SecurityContextHolder.getContext().getAuthentication() instanceof SystemToken token) {
            return token.getTenantId();
        }

        if (TenantContext.getCurrentTenant() != null) {
            return TenantContext.getCurrentTenant();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof Jwt jwt) {
            String tenantId = jwt.getClaim("custom:tenantId");
            if (tenantId == null) {
                tenantId = jwt.getClaim("custom:tenant_id");
            }
            if (tenantId == null) {
                tenantId = jwt.getClaim("custom:tenant");
            }
            if (tenantId == null) {
                tenantId = jwt.getClaim("custom:builder_id");
            }

            if (tenantId != null) {
                try {
                    UUID tenantUUID = UUID.fromString(tenantId);
                    TenantContext.setCurrentTenant(tenantUUID);
                    return tenantUUID;
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid tenant ID in JWT claim: {}. Cannot resolve tenant.", tenantId);
                }
            }
        }

        return null;
    }

    /**
     * Fails the request when an entity does not belong to the caller's tenant.
     *
     * <p>Reports 404 rather than 403 on purpose: telling a caller that a row exists but is not
     * theirs leaks the existence of another tenant's data.
     */
    public void validate(TenantAwareAggregateRoot<?> entity) {
        if (entity == null) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof SystemToken) {
            return;
        }

        if (isPlatform(authentication)) {
            UUID explicitTenantId = TenantContext.getCurrentTenant();
            if (explicitTenantId != null) {
                UUID entityTenantId = entity.getTenant();
                if (entityTenantId != null && !explicitTenantId.equals(entityTenantId)) {
                    log.warn("Tenant mismatch for platform user: context={}, entity={}", explicitTenantId, entityTenantId);
                    throw new LeadratException("Resource not found", HttpStatus.NOT_FOUND);
                }
            }
            return;
        }

        UUID currentTenantId = getTenantId();
        UUID entityTenantId = entity.getTenant();

        if (currentTenantId != null && entityTenantId != null && !currentTenantId.equals(entityTenantId)) {
            log.warn("Tenant mismatch: current={}, entity={}", currentTenantId, entityTenantId);
            throw new LeadratException("Resource not found", HttpStatus.NOT_FOUND);
        }
    }

    /** The caller's UUID, taken from the JWT {@code sub} claim. */
    public UUID getLoggedInUserId() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof SystemToken) {
            return null;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException e) {
                    log.warn("JWT sub is not a valid UUID: {}", sub);
                }
            }
        }
        return null;
    }

    public String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        return "System :: Leadrat";
    }

    public Boolean isAnonymous() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken;
    }

    /** True for TENANT_ADMIN and every platform role. */
    public boolean hasFullAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || isAnonymous()) {
            return false;
        }

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .anyMatch(role -> matches(role, UserRole.TENANT_ADMIN)
                        || matches(role, UserRole.PLATFORM_ADMIN)
                        || matches(role, UserRole.PLATFORM_USER));
    }

    static boolean isPlatform(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .anyMatch(role -> matches(role, UserRole.PLATFORM_ADMIN) || matches(role, UserRole.PLATFORM_USER));
    }

    /** Cognito groups arrive bare; Spring conventions prefix with ROLE_. Accept both. */
    private static boolean matches(String authority, UserRole role) {
        return authority.equals(role.name()) || authority.equals("ROLE_" + role.name());
    }
}
