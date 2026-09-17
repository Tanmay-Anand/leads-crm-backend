package com.leadrat.crm.leads.api.source.sourcetype.dto;

import com.leadrat.crm.leads.api.source.sourcetype.CustomSourceType;

import java.util.UUID;

public record SourceTypeDto(UUID id, String name, String displayName, String colorCode, UUID parentId) {

    public static SourceTypeDto from(CustomSourceType t) {
        return new SourceTypeDto(t.getId(), t.getName(), t.getDisplayName(), t.getColorCode(), t.getParentId());
    }
}
