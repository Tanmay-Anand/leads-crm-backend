package com.leadrat.crm.leads.api.tag.dto;

import com.leadrat.crm.leads.api.tag.CustomTag;

import java.util.UUID;

public record TagDto(UUID id, String name, String displayName, String colorCode) {

    public static TagDto from(CustomTag t) {
        return new TagDto(t.getId(), t.getName(), t.getDisplayName(), t.getColorCode());
    }
}
