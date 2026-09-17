package com.leadrat.crm.leads.api.search.advanced;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FilterCriterion(
        @NotBlank String field,
        @NotNull FilterOperator operator,
        List<String> values
) {
}
