package com.leadrat.crm.leads.api.search.advanced;

/**
 * Sections of the filter drawer, shared by every list screen.
 *
 * <p>The one file here that is deliberately per-service rather than identical across services,
 * because the group vocabulary follows the fields.
 */
public enum FilterGroup {

    // ─── leads ────────────────────────────────────────────────────────────────
    ASSIGNMENT,
    STATUS_AND_SOURCE,
    PROPERTY_REQUIREMENT,
    ADDITIONAL_INFO,

    // ─── projects ─────────────────────────────────────────────────────────────
    PROJECT_DETAILS,
    LOCATION,

    // ─── channel partners ─────────────────────────────────────────────────────
    FIRM,
    COMMISSION,

    // ─── shared ───────────────────────────────────────────────────────────────
    DATES,
    OTHERS
}
