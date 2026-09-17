package com.leadrat.crm.leads.api.channelpartner;

import com.leadrat.crm.leads.api.search.SearchUtils;
import com.leadrat.crm.leads.api.search.SimpleSearchSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Arrays;
import java.util.List;

/** Free-text search across the channel partner list. */
public class ChannelPartnerSimpleSearchSpecification
        extends SimpleSearchSpecification<ChannelPartner, ChannelPartnerSearchField> {

    public ChannelPartnerSimpleSearchSpecification(String query, List<ChannelPartnerSearchField> searchFields) {
        super(query, searchFields);
    }

    @Override
    protected ChannelPartnerSearchField[] allFields() {
        return ChannelPartnerSearchField.values();
    }

    @Override
    protected Predicate buildPredicate(Root<ChannelPartner> root, CriteriaQuery<?> cq,
                                       CriteriaBuilder cb, ChannelPartnerSearchField field, String pattern) {
        return switch (field) {
            case CITY -> like(cb, root.get("address").get("city"), pattern);
            case TIER -> tierIn(root, pattern);
            default -> field.getFieldName() == null
                    ? null
                    : like(cb, root.get(field.getFieldName()), pattern);
        };
    }

    private Predicate like(CriteriaBuilder cb, Path<String> path, String pattern) {
        return cb.like(cb.lower(path), pattern, SearchUtils.ESCAPE);
    }

    private Predicate tierIn(Root<ChannelPartner> root, String pattern) {
        String term = pattern.replace("%", "").toLowerCase();
        if (term.isBlank()) {
            return null;
        }
        List<ChannelPartnerTier> matches = Arrays.stream(ChannelPartnerTier.values())
                .filter(t -> t.name().toLowerCase().contains(term))
                .toList();
        return matches.isEmpty() ? null : root.get("tier").in(matches);
    }
}
