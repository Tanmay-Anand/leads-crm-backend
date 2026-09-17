package com.leadrat.crm.leads.api.tenant;

import java.util.UUID;

/**
 * Per-request tenant, set by {@link TenantFilter} from the {@code x-tenant-id} header or by
 * {@link TenantAware} from the JWT. Cleared at the end of every request — a leaked value would
 * show one tenant another tenant's rows on a reused thread.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(UUID tenant) {
        CURRENT_TENANT.set(tenant);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
