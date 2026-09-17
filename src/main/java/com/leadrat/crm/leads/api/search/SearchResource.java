package com.leadrat.crm.leads.api.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One field/value/type triple, as posted to a {@code POST /{resource}/search} endpoint. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResource {
    private String field;
    private String query;
    private String type;
}
