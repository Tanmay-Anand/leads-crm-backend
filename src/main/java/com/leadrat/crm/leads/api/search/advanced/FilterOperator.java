package com.leadrat.crm.leads.api.search.advanced;

public enum FilterOperator {
    EQ, NE,
    IN, NOT_IN,
    CONTAINS,
    STARTS_WITH,
    GT, GTE, LT, LTE,
    BETWEEN,
    IS_NULL, IS_NOT_NULL,
    ANY_OF
}
