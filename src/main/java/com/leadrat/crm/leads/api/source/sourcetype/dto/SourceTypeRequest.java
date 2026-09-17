package com.leadrat.crm.leads.api.source.sourcetype.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record SourceTypeRequest(
        @NotBlank(message = "name is required") String name,
        String displayName,
        String colorCode,
        UUID parentId
) {
}
