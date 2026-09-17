package com.leadrat.crm.leads.api.leadstatus.dto;

import jakarta.validation.constraints.NotBlank;

public record LeadStatusRequest(
        @NotBlank(message = "name is required") String name,
        String displayName,
        String colorCode,
        Boolean isDefault,
        Boolean isNoteRequired,
        Integer displayOrder
) {
}
