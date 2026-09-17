package com.leadrat.crm.leads.api.lead;

/**
 * Columns the free-text search can be scoped to.
 *
 * <p>A null fieldName means the match is not a plain column LIKE and is built explicitly in
 * {@link LeadSimpleSearchSpecification}.
 */
public enum LeadSearchField {

    /** Matches firstName OR lastName. */
    NAME(null),
    LEAD_CODE("leadCode"),
    MOBILE("mobile"),
    EMAIL("email"),
    ASSIGNED_TO("assignedToUserName"),
    CHANNEL_PARTNER("channelPartnerName"),
    TELECALLER("telecallerName"),
    /** Matched by enum name, case-insensitively, with underscores treated as spaces. */
    PROPERTY_CATEGORY(null),
    /** Resolved against LeadStatus.displayName, then matched as an IN on statusId. */
    STATUS(null),
    /** Resolved against CustomTemperature.displayName, then matched as an IN on temperatureId. */
    TEMPERATURE(null),
    /** Resolved against CustomTag.displayName, then matched as an EXISTS on lead_tag. */
    TAG(null),
    CITY(null);

    private final String fieldName;

    LeadSearchField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
