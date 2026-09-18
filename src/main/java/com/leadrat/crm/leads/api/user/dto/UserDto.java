package com.leadrat.crm.leads.api.user.dto;

import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read model, and the edit request body. Deliberately carries no password field - editing a
 * user can never rotate their password through this shape (see the porting spec's "no password
 * field on the edit form" decision); password reset is a separate, explicitly gated action.
 */
public record UserDto(
        UUID id,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        String displayName,
        UserRole role,
        UUID customRoleId,
        boolean enabled,
        LocalDateTime created,
        String createdBy,
        LocalDateTime modified,
        String lastModifiedBy,
        Boolean isActive
) {

    /** Applies the writable fields onto an entity. Never touches email (see the immutable-email
     *  business guard in UserServiceImpl) or enabled (its own gated endpoint). */
    public void applyTo(User user) {
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setCustomRoleId(customRoleId);
    }

    public static UserDto fromEntity(User user) {
        return new UserDto(
                user.getId(),
                user.getTenant(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getRole(),
                user.getCustomRoleId(),
                user.isEnabled(),
                user.getCreated(),
                user.getCreatedBy(),
                user.getModified(),
                user.getLastModifiedBy(),
                user.isActive());
    }
}
