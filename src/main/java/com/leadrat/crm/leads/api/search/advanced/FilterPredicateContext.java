package com.leadrat.crm.leads.api.search.advanced;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.UUID;

public record FilterPredicateContext<T>(
        Root<T> root,
        CriteriaQuery<?> query,
        CriteriaBuilder cb,
        FilterOperator operator,
        List<Object> coercedValues,
        UUID tenantId
) {
    /** The first coerced value, for the single-value operators. */
    public Object firstValue() {
        return coercedValues.isEmpty() ? null : coercedValues.get(0);
    }
}
