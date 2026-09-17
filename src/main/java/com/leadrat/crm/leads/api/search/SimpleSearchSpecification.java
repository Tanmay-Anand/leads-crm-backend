package com.leadrat.crm.leads.api.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Base for a free-text search across a fixed set of named fields.
 *
 * <p>Subclasses say which fields exist and how each one matches; this class owns the parts that
 * every list screen shares: the two-character minimum, the escaped LIKE pattern, and OR-ing the
 * per-field predicates together. An empty or too-short term matches nothing rather than
 * everything, so a half-typed search never returns the whole table.
 *
 * @param <T> the entity being searched
 * @param <F> the enum naming its searchable fields
 */
public abstract class SimpleSearchSpecification<T, F extends Enum<F>> implements Specification<T> {

    protected final String query;
    protected final List<F> searchFields;

    protected SimpleSearchSpecification(String query, List<F> searchFields) {
        this.query = query;
        this.searchFields = searchFields;
    }

    protected abstract F[] allFields();

    protected abstract Predicate buildPredicate(Root<T> root, CriteriaQuery<?> cq,
                                                CriteriaBuilder cb, F field, String pattern);

    @Override
    public final Predicate toPredicate(Root<T> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
        if (query == null || query.isBlank() || query.trim().length() < 2) {
            return cb.disjunction();
        }

        String pattern = SearchUtils.likePattern(query.toLowerCase());
        List<F> fieldsToSearch = (searchFields == null || searchFields.isEmpty())
                ? Arrays.asList(allFields())
                : searchFields;

        List<Predicate> predicates = new ArrayList<>();
        for (F field : fieldsToSearch) {
            Predicate p = buildPredicate(root, cq, cb, field, pattern);
            if (p != null) {
                predicates.add(p);
            }
        }

        return predicates.isEmpty() ? cb.disjunction() : cb.or(predicates.toArray(new Predicate[0]));
    }
}
