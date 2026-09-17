package com.leadrat.crm.leads.api.source.sourcecategory.dto;

import jakarta.validation.constraints.NotBlank;

public record SourceCategoryRequest(
        @NotBlank(message = "name is required") String name,
        String displayName,
        String colorCode
) {
}
