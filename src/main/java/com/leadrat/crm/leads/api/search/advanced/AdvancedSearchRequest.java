package com.leadrat.crm.leads.api.search.advanced;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record AdvancedSearchRequest(
        @Size(max = 40) @Valid List<FilterCriterion> criteria,
        String q,
        List<String> qFields,
        String scope,
        LocalDate fromDate,
        LocalDate toDate,
        String dateType
) {
}
