package com.leadrat.crm.leads.api.search;

public final class SearchUtils {

    public static final char ESCAPE = '\\';

    private SearchUtils() {
    }

    /** Wraps a raw term in wildcards, escaping the LIKE metacharacters it may contain. */
    public static String likePattern(String raw) {
        return "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
