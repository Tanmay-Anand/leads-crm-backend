package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.search.advanced.ColumnPredicates;
import com.leadrat.crm.leads.api.search.advanced.FilterPredicateContext;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;

import java.util.List;

/**
 * The lead-specific half of the filter registry.
 *
 * <p>Anything that is a plain column is left to the registry built-in. What lands here is what is
 * genuinely about a lead: the embedded address paths and the correlated EXISTS over lead_tag.
 */
public final class LeadFilterPredicates {

    private LeadFilterPredicates() {
    }

    // ─── UUID columns ─────────────────────────────────────────────────────────

    public static Predicate uuidColumn(FilterPredicateContext<Lead> ctx, String column) {
        return ColumnPredicates.uuid(ctx, column);
    }

    public static Predicate primaryOwner(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "assignedTo");
    }

    public static Predicate secondaryOwner(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "telecallerId");
    }

    public static Predicate channelPartner(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "channelPartnerId");
    }

    public static Predicate status(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "statusId");
    }

    public static Predicate temperature(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "temperatureId");
    }

    public static Predicate sourceCategory(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "sourceCategoryId");
    }

    public static Predicate sourceType(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "sourceTypeId");
    }

    public static Predicate createdBy(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "createdByUserId");
    }

    public static Predicate project(FilterPredicateContext<Lead> ctx) {
        return uuidColumn(ctx, "projectId");
    }

    // ─── Enum columns ─────────────────────────────────────────────────────────

    public static Predicate propertyCategory(FilterPredicateContext<Lead> ctx) {
        return ColumnPredicates.enumeration(ctx, "propertyCategory", PropertyCategory.class);
    }

    public static Predicate purchaseTimeline(FilterPredicateContext<Lead> ctx) {
        return ColumnPredicates.enumeration(ctx, "purchaseTimeline", PurchaseTimeline.class);
    }

    public static Predicate assignmentMethod(FilterPredicateContext<Lead> ctx) {
        return ColumnPredicates.enumeration(ctx, "assignmentMethod", AssignmentMethod.class);
    }

    // ─── Embedded address fields ──────────────────────────────────────────────

    public static Predicate city(FilterPredicateContext<Lead> ctx) {
        Path<String> path = ctx.root().get("address").get("city");
        return ColumnPredicates.text(ctx, path);
    }

    public static Predicate state(FilterPredicateContext<Lead> ctx) {
        Path<String> path = ctx.root().get("address").get("state");
        return ColumnPredicates.text(ctx, path);
    }

    // ─── Boolean columns ──────────────────────────────────────────────────────

    public static Predicate isNri(FilterPredicateContext<Lead> ctx) {
        return ColumnPredicates.bool(ctx, "isNri");
    }

    public static Predicate isDraft(FilterPredicateContext<Lead> ctx) {
        return ColumnPredicates.bool(ctx, "isDraft");
    }

    // ─── TAG, a correlated EXISTS on lead_tag ─────────────────────────────────

    /**
     * Matches a lead carrying any of the given tags.
     *
     * <p>An EXISTS rather than a join, because a join would multiply the lead row once per matching
     * tag and break the page count.
     */
    public static Predicate tag(FilterPredicateContext<Lead> ctx) {
        List<String> tagIds = ctx.coercedValues().stream().map(Object::toString).toList();
        if (tagIds.isEmpty()) {
            return ctx.cb().disjunction();
        }

        Subquery<String> sub = ctx.query().subquery(String.class);
        Root<Lead> correlatedRoot = sub.correlate(ctx.root());
        SetJoin<Lead, String> tagJoin = correlatedRoot.joinSet("tagIds");
        sub.select(tagJoin);
        sub.where(tagJoin.in(tagIds));
        return ctx.cb().exists(sub);
    }
}
