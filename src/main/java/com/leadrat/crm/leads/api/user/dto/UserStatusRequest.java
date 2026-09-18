package com.leadrat.crm.leads.api.user.dto;

import jakarta.validation.constraints.NotNull;

/** PATCH /users/{id}/status body - the inline activate/deactivate toggle. */
public record UserStatusRequest(@NotNull(message = "enabled is required") Boolean enabled) {
}
