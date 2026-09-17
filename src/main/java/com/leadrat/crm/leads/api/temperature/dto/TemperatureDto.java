package com.leadrat.crm.leads.api.temperature.dto;

import com.leadrat.crm.leads.api.temperature.CustomTemperature;

import java.util.UUID;

public record TemperatureDto(UUID id, String name, String displayName, String colorCode) {

    public static TemperatureDto from(CustomTemperature t) {
        return new TemperatureDto(t.getId(), t.getName(), t.getDisplayName(), t.getColorCode());
    }
}
