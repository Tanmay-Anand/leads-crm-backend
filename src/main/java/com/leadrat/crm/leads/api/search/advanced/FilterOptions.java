package com.leadrat.crm.leads.api.search.advanced;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for GET /{resource}/filter-options/{optionsSource}, the lazily-fetched dropdown behind
 * any field that declares an optionsSource.
 */
public final class FilterOptions {

    private FilterOptions() {
    }

    /** One option per enum constant, valued by name and labelled in Title Case. */
    public static <E extends Enum<E>> List<FilterOptionDto> ofEnum(E[] values) {
        return Arrays.stream(values).map(FilterOptions::of).toList();
    }

    public static FilterOptionDto of(Enum<?> constant) {
        return new FilterOptionDto(constant.name(), titleCase(constant.name()));
    }

    /** ALLOTMENT_PENDING becomes Allotment Pending. A last resort when nothing configured a label. */
    public static String titleCase(String screamingSnake) {
        return Arrays.stream(screamingSnake.split("_"))
                .filter(w -> !w.isEmpty())
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
