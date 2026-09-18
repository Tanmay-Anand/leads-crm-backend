package com.leadrat.crm.leads.api.rbac;

import lombok.Data;

import java.util.List;

/**
 * What the permission-matrix editor renders from ({@code GET /permissions/catalog}) - never
 * from {@link CrmPermission} directly on the frontend, so a backend addition becomes assignable
 * immediately with no client change.
 */
@Data
public class PermissionCatalogResponse {

    /** Every distinct action word across the whole catalogue, e.g. view, add, update, delete. */
    private List<String> actions;

    private List<ResourceEntry> resources;

    @Data
    public static class ResourceEntry {
        private String key;
        private List<String> actions;
        /** Directly-nested resource keys, e.g. "master-data" -> ["master-data/lead-statuses", ...]. */
        private List<String> children;
    }
}
