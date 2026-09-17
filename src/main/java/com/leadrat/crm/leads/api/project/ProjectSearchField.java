package com.leadrat.crm.leads.api.project;

/**
 * Searchable columns for GET /projects with q and searchFields.
 *
 * <p>A null fieldName means the match is built explicitly in
 * {@link ProjectSimpleSearchSpecification} rather than being a plain column LIKE.
 */
public enum ProjectSearchField {

    NAME("name"),
    BRAND("brand"),
    /** City from the embedded address. */
    CITY(null),
    /** State from the embedded address. */
    STATE(null),
    MICRO_MARKET("microMarket"),
    RERA_NUMBER("reraNumber"),
    RERA_STATE("reraState"),
    /** Matched against the ProjectType enum names. */
    PROJECT_TYPE(null),
    /** Matched against the ProjectStage enum names. */
    PROJECT_STAGE(null);

    private final String fieldName;

    ProjectSearchField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
