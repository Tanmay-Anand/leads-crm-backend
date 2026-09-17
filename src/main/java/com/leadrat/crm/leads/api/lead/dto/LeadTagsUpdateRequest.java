package com.leadrat.crm.leads.api.lead.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Replaces the whole tag set. An empty list clears it. */
public record LeadTagsUpdateRequest(
        @NotNull(message = "tagIds is required") List<UUID> tagIds
) {
}
