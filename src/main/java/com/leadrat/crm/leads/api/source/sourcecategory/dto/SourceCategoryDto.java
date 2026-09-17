package com.leadrat.crm.leads.api.source.sourcecategory.dto;

import com.leadrat.crm.leads.api.source.sourcecategory.CustomSourceCategory;

import java.util.UUID;

public record SourceCategoryDto(UUID id, String name, String displayName, String colorCode) {

    public static SourceCategoryDto from(CustomSourceCategory c) {
        return new SourceCategoryDto(c.getId(), c.getName(), c.getDisplayName(), c.getColorCode());
    }
}
