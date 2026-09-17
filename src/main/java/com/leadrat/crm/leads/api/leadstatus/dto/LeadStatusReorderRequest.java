package com.leadrat.crm.leads.api.leadstatus.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record LeadStatusReorderRequest(
        @NotEmpty(message = "orderedIds is required") List<UUID> orderedIds
) {
}
