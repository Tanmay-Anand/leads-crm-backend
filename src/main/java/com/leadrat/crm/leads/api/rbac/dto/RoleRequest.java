package com.leadrat.crm.leads.api.rbac.dto;

import com.leadrat.crm.leads.api.rbac.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoleRequest(
        @NotBlank(message = "name is required") String name,
        @NotNull(message = "permissions is required") List<String> permissions
) {

    /** Applies the writable fields onto an entity. Never touches {@code isSystem} - that flag is
     *  set once, at seeding time, and this request type has no way to change it. */
    public void applyTo(Role role) {
        role.setName(name);
        role.setPermissions(permissions);
    }

    public Role toEntity() {
        Role role = new Role();
        applyTo(role);
        return role;
    }
}
