package com.leadrat.crm.leads.api.search.advanced;

/** Value and label pair returned by GET /{resource}/filter-options/{optionsSource}. */
public record FilterOptionDto(String value, String label) {
}
