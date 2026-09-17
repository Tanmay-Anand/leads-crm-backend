package com.leadrat.crm.leads.api.lead.dto;

import com.leadrat.crm.leads.api.lead.LeadNoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LeadNoteRequest(
        @NotNull(message = "type is required") LeadNoteType type,
        @NotBlank(message = "body is required") String body,
        String externalReference
) {
}
