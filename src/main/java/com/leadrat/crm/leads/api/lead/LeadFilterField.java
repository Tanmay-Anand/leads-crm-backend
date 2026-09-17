package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.search.advanced.FilterFieldSpec;
import com.leadrat.crm.leads.api.search.advanced.FilterGroup;
import com.leadrat.crm.leads.api.search.advanced.FilterOperator;
import com.leadrat.crm.leads.api.search.advanced.FilterValueType;

import java.util.Set;

import static com.leadrat.crm.leads.api.search.advanced.FilterGroup.ADDITIONAL_INFO;
import static com.leadrat.crm.leads.api.search.advanced.FilterGroup.ASSIGNMENT;
import static com.leadrat.crm.leads.api.search.advanced.FilterGroup.DATES;
import static com.leadrat.crm.leads.api.search.advanced.FilterGroup.OTHERS;
import static com.leadrat.crm.leads.api.search.advanced.FilterGroup.PROPERTY_REQUIREMENT;
import static com.leadrat.crm.leads.api.search.advanced.FilterGroup.STATUS_AND_SOURCE;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.ANY_OF;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.BETWEEN;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.CONTAINS;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.EQ;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.GT;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.GTE;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.IN;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.IS_NOT_NULL;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.IS_NULL;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.LT;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.LTE;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.NE;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.NOT_IN;
import static com.leadrat.crm.leads.api.search.advanced.FilterOperator.STARTS_WITH;
import static com.leadrat.crm.leads.api.search.advanced.FilterValueType.BOOLEAN;
import static com.leadrat.crm.leads.api.search.advanced.FilterValueType.DATETIME;
import static com.leadrat.crm.leads.api.search.advanced.FilterValueType.ENUM;
import static com.leadrat.crm.leads.api.search.advanced.FilterValueType.ID_SET;
import static com.leadrat.crm.leads.api.search.advanced.FilterValueType.LOOKUP;
import static com.leadrat.crm.leads.api.search.advanced.FilterValueType.STRING;

/**
 * Every field the lead advanced search can filter on.
 *
 * <p>The whole filter drawer is generated from this enum: the group is the section it appears in,
 * the value type picks the control, the operator set is what the server will accept, and
 * optionsSource names the dropdown the UI lazily fetches.
 */
public enum LeadFilterField implements FilterFieldSpec {

    // ─── ASSIGNMENT ───────────────────────────────────────────────────────────

    PRIMARY_OWNER("Primary Owner", ASSIGNMENT, LOOKUP, Set.of(IN, NOT_IN, IS_NULL, IS_NOT_NULL), "users", true),
    SECONDARY_OWNER("Telecaller", ASSIGNMENT, LOOKUP, Set.of(IN, NOT_IN, IS_NULL, IS_NOT_NULL), "users", true),
    CHANNEL_PARTNER("Channel Partner", ASSIGNMENT, LOOKUP, Set.of(IN, NOT_IN, IS_NULL, IS_NOT_NULL),
            "channelPartners", true),
    ASSIGNMENT_METHOD("Assignment Method", ASSIGNMENT, ENUM, Set.of(EQ, NE, IN, NOT_IN), "assignmentMethods", false),
    CREATED_BY("Created By", ASSIGNMENT, LOOKUP, Set.of(IN, NOT_IN), "users", true),

    // ─── STATUS AND SOURCE ────────────────────────────────────────────────────

    STATUS("Status", STATUS_AND_SOURCE, LOOKUP, Set.of(IN, NOT_IN), "leadStatuses", true),
    TEMPERATURE("Temperature", STATUS_AND_SOURCE, LOOKUP, Set.of(IN, NOT_IN), "temperatures", true),
    TAG("Tag", STATUS_AND_SOURCE, ID_SET, Set.of(ANY_OF), "tags", true),
    SOURCE_CATEGORY("Source Category", STATUS_AND_SOURCE, LOOKUP, Set.of(IN, NOT_IN), "sourceCategories", true),
    SOURCE_TYPE("Source Type", STATUS_AND_SOURCE, LOOKUP, Set.of(IN, NOT_IN), "sourceTypes", true),
    PROJECT("Project", STATUS_AND_SOURCE, LOOKUP, Set.of(IN, NOT_IN, IS_NULL), "projects", true),

    // ─── PROPERTY REQUIREMENT ─────────────────────────────────────────────────

    PROPERTY_CATEGORY("Property Category", PROPERTY_REQUIREMENT, ENUM, Set.of(EQ, IN), "propertyCategories", false),
    PURCHASE_TIMELINE("Purchase Timeline", PROPERTY_REQUIREMENT, ENUM, Set.of(EQ, IN), "purchaseTimelines", false),

    // ─── ADDITIONAL INFO ──────────────────────────────────────────────────────

    OCCUPATION("Occupation", ADDITIONAL_INFO, STRING, Set.of(CONTAINS, EQ, IS_NULL, IS_NOT_NULL), null, false),
    IS_NRI("Is NRI", ADDITIONAL_INFO, BOOLEAN, Set.of(EQ), null, false),

    // ─── DATES ────────────────────────────────────────────────────────────────

    CREATED_AT("Created At", DATES, DATETIME, Set.of(BETWEEN, GT, GTE, LT, LTE), null, false),
    MODIFIED_AT("Modified At", DATES, DATETIME, Set.of(BETWEEN, GT, GTE, LT, LTE), null, false),
    SCHEDULE_DATE("Schedule Date", DATES, DATETIME, Set.of(BETWEEN, GT, GTE, LT, LTE, IS_NULL, IS_NOT_NULL),
            null, false),

    // ─── OTHERS ───────────────────────────────────────────────────────────────

    IS_DRAFT("Is Draft", OTHERS, BOOLEAN, Set.of(EQ), null, false),
    LEAD_CODE("Lead Code", OTHERS, STRING, Set.of(EQ, CONTAINS, STARTS_WITH), null, false),
    MOBILE("Mobile", OTHERS, STRING, Set.of(EQ, CONTAINS), null, false),
    EMAIL("Email", OTHERS, STRING, Set.of(EQ, CONTAINS, IS_NULL, IS_NOT_NULL), null, false),
    CITY("City", OTHERS, STRING, Set.of(EQ, CONTAINS, IS_NULL, IS_NOT_NULL), null, false),
    STATE("State", OTHERS, STRING, Set.of(EQ, CONTAINS, IS_NULL, IS_NOT_NULL), null, false);

    private final String label;
    private final FilterGroup group;
    private final FilterValueType valueType;
    private final Set<FilterOperator> operators;
    private final String optionsSource;
    private final boolean multi;

    LeadFilterField(String label, FilterGroup group, FilterValueType valueType,
                    Set<FilterOperator> operators, String optionsSource, boolean multi) {
        this.label = label;
        this.group = group;
        this.valueType = valueType;
        this.operators = operators;
        this.optionsSource = optionsSource;
        this.multi = multi;
    }

    @Override
    public String key() {
        return name();
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public FilterGroup group() {
        return group;
    }

    @Override
    public FilterValueType valueType() {
        return valueType;
    }

    @Override
    public Set<FilterOperator> operators() {
        return operators;
    }

    @Override
    public String optionsSource() {
        return optionsSource;
    }

    @Override
    public boolean multi() {
        return multi;
    }
}
