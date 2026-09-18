package com.leadrat.crm.leads.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH /users/{id}/password body - the gated, out-of-band password reset row action. */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password
) {
}
