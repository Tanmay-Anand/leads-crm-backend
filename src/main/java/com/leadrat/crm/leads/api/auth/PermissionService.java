package com.leadrat.crm.leads.api.auth;

import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.rbac.CrmPermission;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import com.leadrat.crm.leads.api.util.SystemToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The SpEL entry point for every {@code @PreAuthorize("@permissionService.check(...)")} guard in
 * this app.
 *
 * <p><strong>Exactly one guard per handler method.</strong> Stacking a role annotation
 * ({@code @TenantAdminOnly} etc.) and a permission check on one method does not work: both are
 * {@code @PreAuthorize} under the hood, and Spring's {@code AuthorizationAnnotationUtils
 * .findUniqueAnnotation} throws {@code AnnotationConfigurationException} when it finds two on
 * one method - thrown lazily, on first invocation, so it surfaces as a 500 on that one endpoint
 * rather than a startup failure. Use a role annotation for endpoints with no resource permission
 * of their own; use {@code check(...)} for everything else. {@code ControllerGuardCoverageTest}
 * enforces this.
 *
 * <p><strong>Resolution order:</strong> null authentication -> deny; {@link SystemToken} ->
 * allow (internal, non-request-bound work); {@code PLATFORM_ADMIN}/{@code TENANT_ADMIN} -> allow
 * with zero queries; otherwise, a cached lookup through {@link EffectivePermissionLoader}.
 * {@code ROLE_UNKNOWN} (the empty-Cognito-groups sentinel) matches no {@link UserRole} and
 * therefore denies everything.
 *
 * <p>Two deliberate deviations from the reference this was ported from:
 * <ul>
 *   <li><strong>{@code PLATFORM_USER} is excluded from the admin short-circuit.</strong> The
 *   reference includes it, giving blanket bypass to the one role whose entire purpose is
 *   read-only access - defeating its own {@code delete:*} exclusion in {@link
 *   #deriveDefaultPermissions}.</li>
 *   <li><strong>On loader failure, this falls back to {@link #deriveDefaultPermissions}, not
 *   deny.</strong> The reference denies because its failure mode is an unreachable
 *   <em>service</em> call; this one's is an unreachable <em>table</em>, and the JWT group is an
 *   independently signed credential. The fallback is never more permissive than the JWT ceiling
 *   itself, so a database blip degrades rather than producing a wall of 403s that looks like a
 *   config bug.</li>
 * </ul>
 */
@Slf4j
@Service("permissionService")
@RequiredArgsConstructor
public class PermissionService {

    private final EffectivePermissionLoader loader;
    private final PermissionCache cache;
    private final TenantAware tenantAware;

    /** The SpEL entry point: {@code @PreAuthorize("@permissionService.check('view', 'leads')")}. */
    public boolean check(String action, String resource) {
        return check(CrmPermission.of(action, resource));
    }

    private boolean check(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        if (authentication instanceof SystemToken) {
            return true;
        }
        if (isAdmin(authentication)) {
            return true;
        }

        UUID tenantId = tenantAware.getTenantId();
        UUID userId = tenantAware.getLoggedInUserId();
        if (tenantId == null || userId == null) {
            return false;
        }

        return effectivePermissions(tenantId, userId, authentication).contains(permission);
    }

    /** True only for {@code PLATFORM_ADMIN}/{@code TENANT_ADMIN} - see the class doc for why
     *  {@code PLATFORM_USER} is deliberately excluded. */
    public boolean isAdmin(Authentication authentication) {
        return Authorities.has(authentication, UserRole.PLATFORM_ADMIN)
                || Authorities.has(authentication, UserRole.TENANT_ADMIN);
    }

    public List<String> effectivePermissions(UUID tenantId, UUID userId) {
        return effectivePermissions(tenantId, userId, SecurityContextHolder.getContext().getAuthentication());
    }

    private List<String> effectivePermissions(UUID tenantId, UUID userId, Authentication authentication) {
        try {
            return cache.get(tenantId, userId, () -> loader.load(tenantId, userId));
        } catch (RuntimeException e) {
            log.warn("Permission lookup failed for {}/{} - falling back to the JWT role's defaults: {}",
                    tenantId, userId, e.toString());
            UserRole jwtRole = Authorities.resolve(authentication).orElse(null);
            return deriveDefaultPermissions(jwtRole);
        }
    }

    public void evictUser(UUID tenantId, UUID userId) {
        cache.evictUser(tenantId, userId);
    }

    public void evictTenant(UUID tenantId) {
        cache.evictTenant(tenantId);
    }

    /**
     * The ceiling a {@link UserRole} caps a custom role's permissions at - the "Layer 2 can only
     * subtract from Layer 1, never add" invariant, in one place.
     *
     * <p>{@code TENANT_USER}/{@code PLATFORM_USER} exclude every {@code delete:*} permission
     * (matching the reference's own fallback) <strong>and</strong> {@code add:users} - the
     * reference's default includes it, which lets a {@code TENANT_USER} create a brand new
     * {@code TENANT_ADMIN} user and hand themselves the keys. Creating a user sets that user's
     * Layer 1 ceiling directly (their Cognito group), which nothing at Layer 2 can subsequently
     * cap - unlike every other permission here, there is no intersection step downstream that
     * would catch this one after the fact, so it has to be excluded at the source.
     */
    public static List<String> deriveDefaultPermissions(UserRole role) {
        if (role == null) {
            return List.of();
        }
        List<String> all = new ArrayList<>(CrmPermission.ALL);
        return switch (role) {
            case PLATFORM_ADMIN, TENANT_ADMIN -> all;
            case TENANT_USER -> all.stream()
                    .filter(p -> !p.startsWith("delete:"))
                    .filter(p -> !p.equals("add:users"))
                    .collect(Collectors.toList());
            // Leadrat's own cross-tenant platform staff, not a tenant's lead-facing team - never
            // has a customer meeting to brief, so it is excluded even though every other view:*
            // permission is available to it same as TENANT_USER.
            case PLATFORM_USER -> all.stream()
                    .filter(p -> !p.startsWith("delete:"))
                    .filter(p -> !p.equals("add:users"))
                    .filter(p -> !p.equals("view:ai-briefing"))
                    .collect(Collectors.toList());
        };
    }
}
