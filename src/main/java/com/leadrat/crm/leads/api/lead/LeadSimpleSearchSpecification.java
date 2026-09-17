package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.search.SearchUtils;
import com.leadrat.crm.leads.api.search.SimpleSearchSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Free-text search across the lead list.
 *
 * <p>Three kinds of field appear here. Plain columns match with a LIKE. The embedded address needs
 * a nested path. Status, temperature and tag live in other tables and are keyed by id on the lead,
 * so the service resolves the search term to a set of ids first and passes them in; matching them
 * here is then an IN, or for tags an EXISTS, rather than a join. Pre-resolving rather than joining
 * keeps this a single query over the leads table and keeps the page count right.
 */
public class LeadSimpleSearchSpecification extends SimpleSearchSpecification<Lead, LeadSearchField> {

    private final Collection<UUID> matchedStatusIds;
    private final Collection<UUID> matchedTemperatureIds;
    private final Collection<UUID> matchedTagIds;

    public LeadSimpleSearchSpecification(String query,
                                         List<LeadSearchField> searchFields,
                                         Collection<UUID> matchedStatusIds,
                                         Collection<UUID> matchedTemperatureIds,
                                         Collection<UUID> matchedTagIds) {
        super(query, searchFields);
        this.matchedStatusIds = matchedStatusIds;
        this.matchedTemperatureIds = matchedTemperatureIds;
        this.matchedTagIds = matchedTagIds;
    }

    @Override
    protected LeadSearchField[] allFields() {
        return LeadSearchField.values();
    }

    @Override
    protected Predicate buildPredicate(Root<Lead> root, CriteriaQuery<?> cq,
                                       CriteriaBuilder cb, LeadSearchField field, String pattern) {
        return switch (field) {
            case NAME -> cb.or(
                    like(cb, root.get("firstName"), pattern),
                    like(cb, root.get("lastName"), pattern));

            case PROPERTY_CATEGORY -> propertyCategoryPredicate(cb, root, pattern);

            case STATUS -> idIn(cb, root, "statusId", matchedStatusIds);

            case TEMPERATURE -> idIn(cb, root, "temperatureId", matchedTemperatureIds);

            case TAG -> tagExists(cb, cq, root);

            case CITY -> like(cb, root.get("address").get("city"), pattern);

            // Everything else is a plain column named by the enum.
            default -> field.getFieldName() == null
                    ? null
                    : like(cb, root.get(field.getFieldName()), pattern);
        };
    }

    private Predicate like(CriteriaBuilder cb, jakarta.persistence.criteria.Path<String> path, String pattern) {
        return cb.like(cb.lower(path), pattern, SearchUtils.ESCAPE);
    }

    /**
     * Matches the enum by name, tolerating the spacing a user would actually type: searching for
     * "residential" finds RESIDENTIAL, and "site visit" would find SITE_VISIT.
     */
    private Predicate propertyCategoryPredicate(CriteriaBuilder cb, Root<Lead> root, String pattern) {
        String term = pattern.replace("%", "").replace(" ", "_");
        if (term.isBlank()) {
            return null;
        }
        List<PropertyCategory> matches = java.util.Arrays.stream(PropertyCategory.values())
                .filter(c -> c.name().toLowerCase().contains(term.toLowerCase()))
                .toList();
        return matches.isEmpty() ? null : root.get("propertyCategory").in(matches);
    }

    private Predicate idIn(CriteriaBuilder cb, Root<Lead> root, String column, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return root.get(column).in(ids);
    }

    private Predicate tagExists(CriteriaBuilder cb, CriteriaQuery<?> cq, Root<Lead> root) {
        if (matchedTagIds == null || matchedTagIds.isEmpty()) {
            return null;
        }
        List<String> ids = matchedTagIds.stream().map(UUID::toString).toList();

        Subquery<String> sub = cq.subquery(String.class);
        Root<Lead> correlatedRoot = sub.correlate(root);
        SetJoin<Lead, String> tagJoin = correlatedRoot.joinSet("tagIds");
        sub.select(tagJoin);
        sub.where(tagJoin.in(ids));
        return cb.exists(sub);
    }
}
