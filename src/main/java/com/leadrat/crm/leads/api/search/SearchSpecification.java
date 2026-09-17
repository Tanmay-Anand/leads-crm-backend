package com.leadrat.crm.leads.api.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Turns a single {@link SearchResource} into a predicate. An unrecognised type falls through to a
 * case-insensitive LIKE, which is what the untyped legacy search endpoints expect.
 */
public class SearchSpecification<T> implements Specification<T> {

    private final SearchResource searchResource;

    public SearchSpecification(SearchResource searchResource) {
        this.searchResource = searchResource;
    }

    @Override
    public Predicate toPredicate(@NotNull Root<T> entity, @NotNull CriteriaQuery<?> criteriaQuery,
                                 @NotNull CriteriaBuilder cb) {

        if (searchResource.getQuery() == null || searchResource.getField() == null) {
            return cb.conjunction();
        }

        String field = searchResource.getField();
        String query = searchResource.getQuery();
        String type = searchResource.getType();

        if (type != null) {
            switch (type) {
                case "UUID":
                    return cb.equal(entity.get(field), UUID.fromString(query));
                case "Date":
                    return cb.equal(entity.get(field), LocalDate.parse(query));
                case "DateTime":
                    return cb.equal(entity.get(field), LocalDateTime.parse(query));
                case "Integer":
                    return cb.equal(entity.get(field), Integer.parseInt(query));
                case "Long":
                    return cb.equal(entity.get(field), Long.parseLong(query));
                case "Boolean":
                    return cb.equal(entity.get(field), Boolean.parseBoolean(query));
                case "BigDecimal":
                    return cb.equal(entity.get(field), new BigDecimal(query));
                case "Double":
                    return cb.equal(entity.get(field), Double.parseDouble(query));
                case "UUID_IN": {
                    CriteriaBuilder.In<Object> inClause = cb.in(entity.get(field));
                    for (String id : query.split(",")) {
                        inClause.value(UUID.fromString(id.trim()));
                    }
                    return inClause;
                }
                case "StringExact":
                    return cb.equal(cb.lower(entity.get(field).as(String.class)), query.toLowerCase());
                case "DateRange": {
                    String[] parts = query.split(",");
                    return cb.between(entity.get(field),
                            LocalDate.parse(parts[0].trim()), LocalDate.parse(parts[1].trim()));
                }
                case "DateTimeRange": {
                    String[] parts = query.split(",");
                    return cb.between(entity.get(field),
                            LocalDateTime.parse(parts[0].trim()), LocalDateTime.parse(parts[1].trim()));
                }
                default:
                    break;
            }
        }

        return cb.like(cb.lower(entity.get(field).as(String.class)), "%" + query.toLowerCase() + "%");
    }
}
