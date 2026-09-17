package com.leadrat.crm.leads.api.tag.dto;

import jakarta.validation.constraints.NotBlank;

public record TagRequest(
        @NotBlank(message = "name is required") String name,
        String displayName,
        String colorCode
) {
}
