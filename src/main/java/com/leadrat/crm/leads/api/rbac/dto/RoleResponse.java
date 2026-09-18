package com.leadrat.crm.leads.api.rbac.dto;

import com.leadrat.crm.leads.api.rbac.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        List<String> permissions,
        boolean isSystem,
        /** How many users currently hold this as their custom role. */
        long userCount,
        LocalDateTime created,
        String createdBy,
        LocalDateTime modified,
        String lastModifiedBy
) {

    public static RoleResponse fromEntity(Role role, long userCount) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getPermissions(),
                role.isSystem(),
                userCount,
                role.getCreated(),
                role.getCreatedBy(),
                role.getModified(),
                role.getLastModifiedBy());
    }
}
