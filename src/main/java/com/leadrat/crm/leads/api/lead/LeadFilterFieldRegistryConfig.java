package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.search.advanced.FilterFieldRegistry;
import com.leadrat.crm.leads.api.search.advanced.FilterPredicateFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Wires each {@link LeadFilterField} to how it becomes a predicate.
 *
 * <p>A field appears in {@code factories} when it needs custom SQL, in {@code columnPaths} when it
 * is a plain column whose entity attribute is spelled differently from the enum key, and in neither
 * when the key already is the attribute name.
 */
@Configuration
public class LeadFilterFieldRegistryConfig {

    @Bean
    public FilterFieldRegistry<Lead, LeadFilterField> leadFilterFieldRegistry() {
        Map<LeadFilterField, FilterPredicateFactory<Lead>> factories = new EnumMap<>(LeadFilterField.class);
        Map<LeadFilterField, String> columnPaths = new EnumMap<>(LeadFilterField.class);

        // ─── ASSIGNMENT ───────────────────────────────────────────────────────
        factories.put(LeadFilterField.PRIMARY_OWNER, LeadFilterPredicates::primaryOwner);
        factories.put(LeadFilterField.SECONDARY_OWNER, LeadFilterPredicates::secondaryOwner);
        factories.put(LeadFilterField.CHANNEL_PARTNER, LeadFilterPredicates::channelPartner);
        factories.put(LeadFilterField.ASSIGNMENT_METHOD, LeadFilterPredicates::assignmentMethod);
        factories.put(LeadFilterField.CREATED_BY, LeadFilterPredicates::createdBy);

        // ─── STATUS AND SOURCE ────────────────────────────────────────────────
        factories.put(LeadFilterField.STATUS, LeadFilterPredicates::status);
        factories.put(LeadFilterField.TEMPERATURE, LeadFilterPredicates::temperature);
        factories.put(LeadFilterField.TAG, LeadFilterPredicates::tag);
        factories.put(LeadFilterField.SOURCE_CATEGORY, LeadFilterPredicates::sourceCategory);
        factories.put(LeadFilterField.SOURCE_TYPE, LeadFilterPredicates::sourceType);
        factories.put(LeadFilterField.PROJECT, LeadFilterPredicates::project);

        // ─── PROPERTY REQUIREMENT ─────────────────────────────────────────────
        factories.put(LeadFilterField.PROPERTY_CATEGORY, LeadFilterPredicates::propertyCategory);
        factories.put(LeadFilterField.PURCHASE_TIMELINE, LeadFilterPredicates::purchaseTimeline);

        // ─── ADDITIONAL INFO ──────────────────────────────────────────────────
        columnPaths.put(LeadFilterField.OCCUPATION, "occupation");
        factories.put(LeadFilterField.IS_NRI, LeadFilterPredicates::isNri);

        // ─── DATES ────────────────────────────────────────────────────────────
        columnPaths.put(LeadFilterField.CREATED_AT, "created");
        columnPaths.put(LeadFilterField.MODIFIED_AT, "modified");
        columnPaths.put(LeadFilterField.SCHEDULE_DATE, "scheduleDate");

        // ─── OTHERS ───────────────────────────────────────────────────────────
        factories.put(LeadFilterField.IS_DRAFT, LeadFilterPredicates::isDraft);
        columnPaths.put(LeadFilterField.LEAD_CODE, "leadCode");
        columnPaths.put(LeadFilterField.MOBILE, "mobile");
        columnPaths.put(LeadFilterField.EMAIL, "email");
        factories.put(LeadFilterField.CITY, LeadFilterPredicates::city);
        factories.put(LeadFilterField.STATE, LeadFilterPredicates::state);

        return new FilterFieldRegistry<>(LeadFilterField.class, factories, columnPaths);
    }
}
