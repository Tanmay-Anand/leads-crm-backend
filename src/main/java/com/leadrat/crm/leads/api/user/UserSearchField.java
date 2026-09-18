package com.leadrat.crm.leads.api.user;

/** Searchable columns for GET /users with q and searchFields. */
public enum UserSearchField {

    EMAIL("email"),
    FIRST_NAME("firstName"),
    LAST_NAME("lastName");

    private final String fieldName;

    UserSearchField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
