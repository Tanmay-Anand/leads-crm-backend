package com.leadrat.crm.leads.api.search.advanced;

import java.util.List;

public record FilterFieldDto(
        String key,
        String label,
        FilterGroup group,
        FilterValueType valueType,
        List<FilterOperator> operators,
        String optionsSource,
        boolean multi
) {
    public static FilterFieldDto from(FilterFieldSpec spec) {
        return new FilterFieldDto(
                spec.key(),
                spec.label(),
                spec.group(),
                spec.valueType(),
                List.copyOf(spec.operators()),
                spec.optionsSource(),
                spec.multi());
    }
}
