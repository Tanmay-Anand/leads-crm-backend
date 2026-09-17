package com.leadrat.crm.leads.api.search.advanced;

import jakarta.persistence.criteria.Predicate;

@FunctionalInterface
public interface FilterPredicateFactory<T> {
    Predicate build(FilterPredicateContext<T> ctx);
}
