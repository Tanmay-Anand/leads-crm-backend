package com.leadrat.crm.leads.api.search.advanced;

import java.util.Set;

/**
 * What a filterable field declares about itself. Implemented by a per-module enum, which is what
 * lets the registry stay entity-agnostic.
 */
public interface FilterFieldSpec {

    String key();

    String label();

    FilterGroup group();

    FilterValueType valueType();

    Set<FilterOperator> operators();

    /** Names the dropdown the UI should lazily fetch for this field. Null means free entry. */
    default String optionsSource() {
        return null;
    }

    default boolean multi() {
        return false;
    }
}
