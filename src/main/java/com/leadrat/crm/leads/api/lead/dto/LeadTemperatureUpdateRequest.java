package com.leadrat.crm.leads.api.lead.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LeadTemperatureUpdateRequest(
        @NotNull(message = "temperatureId is required") UUID temperatureId
) {
}
