package com.leadrat.crm.leads.api.auth;

import java.util.UUID;

/**
 * Published on any change that could alter what someone is allowed to do: a role's permission
 * list, or a single user's role/custom-role/enabled state. Consumed only after commit (see
 * {@link PermissionCacheEvictionListener}) - an in-transaction evict would let a concurrent
 * reader repopulate the cache from pre-commit state that then survives a rollback.
 *
 * @param tenantId the tenant to evict
 * @param userId   a single user to evict, or null to evict the whole tenant (a role changed)
 */
public record PermissionsChangedEvent(UUID tenantId, UUID userId) {

    public static PermissionsChangedEvent forUser(UUID tenantId, UUID userId) {
        return new PermissionsChangedEvent(tenantId, userId);
    }

    public static PermissionsChangedEvent forTenant(UUID tenantId) {
        return new PermissionsChangedEvent(tenantId, null);
    }
}
