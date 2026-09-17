package com.leadrat.crm.leads.api.search.advanced;

import com.leadrat.crm.leads.api.exception.LeadratException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-entity registry of filterable fields.
 *
 * <p>A field either names a plain column, handled by the built-in predicate below, or supplies its
 * own factory for anything that needs a join, a subquery or an embedded path. That split is what
 * keeps each module holding only the predicates that are genuinely about its entity.
 *
 * @param <T> the entity
 * @param <F> the enum naming its filterable fields
 */
public final class FilterFieldRegistry<T, F extends Enum<F> & FilterFieldSpec> {

    private final Class<F> type;
    private final Map<F, FilterPredicateFactory<T>> factories;

    /** Column-path override for simple fields. Absent means the enum key doubles as the path. */
    private final Map<F, String> columnPaths;

    public FilterFieldRegistry(Class<F> type,
                               Map<F, FilterPredicateFactory<T>> factories,
                               Map<F, String> columnPaths) {
        this.type = type;
        this.factories = new EnumMap<>(factories);
        this.columnPaths = new EnumMap<>(columnPaths);
    }

    public F lookup(String key) {
        try {
            return Enum.valueOf(type, key);
        } catch (IllegalArgumentException e) {
            throw new LeadratException("Unknown filter field: " + key, HttpStatus.BAD_REQUEST);
        }
    }

    public void assertOperatorAllowed(F field, FilterOperator operator) {
        if (!field.operators().contains(operator)) {
            throw new LeadratException(
                    "Operator " + operator + " is not allowed for field " + field.key(),
                    HttpStatus.BAD_REQUEST);
        }
    }

    public Specification<T> specFor(F field, FilterOperator operator, List<Object> coercedValues) {
        return specFor(field, operator, coercedValues, null);
    }

    public Specification<T> specFor(F field, FilterOperator operator, List<Object> coercedValues, UUID tenantId) {
        return (root, cq, cb) -> {
            FilterPredicateFactory<T> factory = factories.get(field);
            if (factory != null) {
                return factory.build(new FilterPredicateContext<>(root, cq, cb, operator, coercedValues, tenantId));
            }
            return defaultPredicate(root, cb, field, operator, coercedValues);
        };
    }

    public List<FilterFieldDto> fieldDtos() {
        List<FilterFieldDto> result = new ArrayList<>();
        for (F constant : type.getEnumConstants()) {
            result.add(FilterFieldDto.from(constant));
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate defaultPredicate(Root<T> root, CriteriaBuilder cb,
                                       F field, FilterOperator operator, List<Object> values) {
        String columnName = columnPaths.getOrDefault(field, field.key());
        Path path = root.get(columnName);

        // A half-filled control must neither narrow the result set nor blow up: every value-taking
        // operator falls back to conjunction, a no-op, when the request carried no usable value.
        // The frontend blocks this before submit; the server must not rely on that.
        boolean noValue = values.isEmpty() || values.get(0) == null;

        return switch (operator) {
            case EQ -> noValue ? cb.conjunction() : cb.equal(path, values.get(0));
            case NE -> noValue ? cb.conjunction() : cb.notEqual(path, values.get(0));
            case IN -> values.isEmpty() ? cb.disjunction() : path.in(values);
            case NOT_IN -> values.isEmpty() ? cb.conjunction() : cb.not(path.in(values));
            case CONTAINS -> {
                if (noValue) yield cb.conjunction();
                String pattern = "%" + escapeLike(String.valueOf(values.get(0))) + "%";
                yield cb.like(cb.lower(path), pattern.toLowerCase(), ESCAPE);
            }
            case STARTS_WITH -> {
                if (noValue) yield cb.conjunction();
                String pattern = escapeLike(String.valueOf(values.get(0))) + "%";
                yield cb.like(cb.lower(path), pattern.toLowerCase(), ESCAPE);
            }
            case GT -> noValue ? cb.conjunction() : cb.greaterThan(path, (Comparable) values.get(0));
            case GTE -> noValue ? cb.conjunction() : cb.greaterThanOrEqualTo(path, (Comparable) values.get(0));
            case LT -> noValue ? cb.conjunction() : cb.lessThan(path, (Comparable) values.get(0));
            case LTE -> noValue ? cb.conjunction() : cb.lessThanOrEqualTo(path, (Comparable) values.get(0));
            case BETWEEN -> {
                if (values.size() < 2 || values.get(0) == null || values.get(1) == null) yield cb.conjunction();
                yield cb.between(path, (Comparable) values.get(0), (Comparable) values.get(1));
            }
            case IS_NULL -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
            case ANY_OF -> values.isEmpty() ? cb.disjunction() : path.in(values);
        };
    }

    private static final char ESCAPE = ColumnPredicates.ESCAPE;

    private static String escapeLike(String s) {
        return ColumnPredicates.escapeLike(s);
    }
}
