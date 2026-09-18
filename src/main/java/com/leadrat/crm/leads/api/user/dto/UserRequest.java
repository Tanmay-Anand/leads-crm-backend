package com.leadrat.crm.leads.api.user.dto;

import com.leadrat.crm.leads.api.core.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create request only - carries a password, since Cognito provisioning needs one up front
 * ({@code AdminSetUserPassword}). {@link UserDto} is the edit request body, and has no password
 * field at all: two schemas, so a stray edit-form submission can never rotate someone else's
 * password.
 */
public record UserRequest(
        @NotBlank @Email(message = "a valid email is required") String email,
        String firstName,
        String lastName,
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
        @NotNull(message = "role is required") UserRole role,
        UUID customRoleId
) {
}
