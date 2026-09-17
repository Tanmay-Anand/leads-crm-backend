package com.leadrat.crm.leads.api.channelpartner;

/**
 * Searchable columns for the channel partner list.
 *
 * <p>A null fieldName means the match is built explicitly in
 * {@link ChannelPartnerSimpleSearchSpecification}.
 */
public enum ChannelPartnerSearchField {

    FIRM_NAME("name"),
    OWNER_NAME("ownerPocName"),
    EMAIL("email"),
    PHONE("primaryPhone"),
    RERA_NUMBER("reraRegNumber"),
    /** City from the embedded address. */
    CITY(null),
    /** Matched against the ChannelPartnerTier enum names. */
    TIER(null);

    private final String fieldName;

    ChannelPartnerSearchField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
