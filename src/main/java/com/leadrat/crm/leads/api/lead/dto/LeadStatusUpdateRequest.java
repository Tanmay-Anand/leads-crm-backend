package com.leadrat.crm.leads.api.lead.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Moves a lead to a status.
 *
 * <p>note is required when the target status is configured with isNoteRequired, which the service
 * enforces rather than the annotation, because the requirement depends on the target row.
 */
public record LeadStatusUpdateRequest(
        @NotNull(message = "statusId is required") UUID statusId,
        String note
) {
}
