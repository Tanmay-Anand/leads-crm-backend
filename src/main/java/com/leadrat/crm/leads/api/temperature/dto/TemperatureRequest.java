package com.leadrat.crm.leads.api.temperature.dto;

import jakarta.validation.constraints.NotBlank;

public record TemperatureRequest(
        @NotBlank(message = "name is required") String name,
        String displayName,
        String colorCode
) {
}
