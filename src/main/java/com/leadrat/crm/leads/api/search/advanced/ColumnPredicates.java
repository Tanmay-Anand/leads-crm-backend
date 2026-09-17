package com.leadrat.crm.leads.api.search.advanced;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Predicate builders for the column shapes that recur in every module: a UUID column, a
 * case-insensitive text column, an enum column, a boolean column.
 *
 * <p>Everything here is entity-agnostic, so a module predicate class is left holding only what is
 * genuinely about that entity: the correlated EXISTS queries, the derived flags, the embedded paths.
 *
 * <p>These builders never throw on a missing value. A binary operator with no value yields
 * conjunction, matching everything and so narrowing nothing, and a membership operator with an
 * empty list yields disjunction, matching nothing. Same contract as the registry built-in.
 */
public final class ColumnPredicates {

    private ColumnPredicates() {
    }

    // ─── UUID columns ─────────────────────────────────────────────────────────

    public static <T> Predicate uuid(FilterPredicateContext<T> ctx, String column) {
        Path<UUID> path = ctx.root().get(column);
        List<UUID> values = ctx.coercedValues().stream()
                .filter(UUID.class::isInstance)
                .map(UUID.class::cast)
                .toList();
        return switch (ctx.operator()) {
            case IN, ANY_OF -> values.isEmpty() ? ctx.cb().disjunction() : path.in(values);
            case NOT_IN -> values.isEmpty() ? ctx.cb().conjunction() : ctx.cb().not(path.in(values));
            case EQ -> values.isEmpty() ? ctx.cb().conjunction() : ctx.cb().equal(path, values.get(0));
            case NE -> values.isEmpty() ? ctx.cb().conjunction() : ctx.cb().notEqual(path, values.get(0));
            case IS_NULL -> ctx.cb().isNull(path);
            case IS_NOT_NULL -> ctx.cb().isNotNull(path);
            default -> ctx.cb().conjunction();
        };
    }

    // ─── Text columns ─────────────────────────────────────────────────────────

    public static <T> Predicate text(FilterPredicateContext<T> ctx, String column) {
        return text(ctx, ctx.root().get(column));
    }

    /** Overload for an embedded or otherwise nested path, such as address then city. */
    public static <T> Predicate text(FilterPredicateContext<T> ctx, Path<String> path) {
        List<String> values = ctx.coercedValues().stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::toLowerCase)
                .toList();
        String first = values.isEmpty() ? null : values.get(0);

        return switch (ctx.operator()) {
            case EQ -> first == null ? ctx.cb().conjunction() : ctx.cb().equal(ctx.cb().lower(path), first);
            case NE -> first == null ? ctx.cb().conjunction() : ctx.cb().notEqual(ctx.cb().lower(path), first);
            case IN, ANY_OF -> values.isEmpty() ? ctx.cb().disjunction() : ctx.cb().lower(path).in(values);
            case NOT_IN -> values.isEmpty()
                    ? ctx.cb().conjunction()
                    : ctx.cb().not(ctx.cb().lower(path).in(values));
            case CONTAINS -> first == null
                    ? ctx.cb().conjunction()
                    : ctx.cb().like(ctx.cb().lower(path), "%" + escapeLike(first) + "%", ESCAPE);
            case STARTS_WITH -> first == null
                    ? ctx.cb().conjunction()
                    : ctx.cb().like(ctx.cb().lower(path), escapeLike(first) + "%", ESCAPE);
            case IS_NULL -> ctx.cb().isNull(path);
            case IS_NOT_NULL -> ctx.cb().isNotNull(path);
            default -> ctx.cb().conjunction();
        };
    }

    /**
     * Range and equality over a nested numeric or temporal path. The registry built-in resolves a
     * single segment only, so anything nested needs this.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Predicate comparable(FilterPredicateContext<T> ctx, Path path) {
        List<Object> values = ctx.coercedValues();
        boolean noValue = values.isEmpty() || values.get(0) == null;

        return switch (ctx.operator()) {
            case EQ -> noValue ? ctx.cb().conjunction() : ctx.cb().equal(path, values.get(0));
            case NE -> noValue ? ctx.cb().conjunction() : ctx.cb().notEqual(path, values.get(0));
            case GT -> noValue ? ctx.cb().conjunction() : ctx.cb().greaterThan(path, (Comparable) values.get(0));
            case GTE -> noValue ? ctx.cb().conjunction()
                    : ctx.cb().greaterThanOrEqualTo(path, (Comparable) values.get(0));
            case LT -> noValue ? ctx.cb().conjunction() : ctx.cb().lessThan(path, (Comparable) values.get(0));
            case LTE -> noValue ? ctx.cb().conjunction()
                    : ctx.cb().lessThanOrEqualTo(path, (Comparable) values.get(0));
            case BETWEEN -> {
                if (values.size() < 2 || values.get(0) == null || values.get(1) == null) {
                    yield ctx.cb().conjunction();
                }
                yield ctx.cb().between(path, (Comparable) values.get(0), (Comparable) values.get(1));
            }
            case IS_NULL -> ctx.cb().isNull(path);
            case IS_NOT_NULL -> ctx.cb().isNotNull(path);
            default -> ctx.cb().conjunction();
        };
    }

    // ─── Enum columns ─────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, E extends Enum<E>> Predicate enumeration(FilterPredicateContext<T> ctx,
                                                               String column, Class<E> enumType) {
        List<E> values = ctx.coercedValues().stream()
                .filter(Objects::nonNull)
                .map(o -> parse(enumType, String.valueOf(o)))
                .filter(Objects::nonNull)
                .toList();
        Path path = ctx.root().get(column);

        return switch (ctx.operator()) {
            // An IN over values that all failed to parse must match nothing, not everything,
            // otherwise a stale saved filter quietly widens the result set.
            case IN, EQ, ANY_OF -> values.isEmpty() ? ctx.cb().disjunction() : path.in(values);
            case NOT_IN, NE -> values.isEmpty() ? ctx.cb().conjunction() : ctx.cb().not(path.in(values));
            case IS_NULL -> ctx.cb().isNull(path);
            case IS_NOT_NULL -> ctx.cb().isNotNull(path);
            default -> ctx.cb().conjunction();
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> enumType, String raw) {
        try {
            return Enum.valueOf(enumType, raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ─── Boolean columns ──────────────────────────────────────────────────────

    /** An absent value reads as true, matching how the drawer renders a bare boolean control. */
    public static <T> Predicate bool(FilterPredicateContext<T> ctx, String column) {
        return ctx.cb().equal(ctx.root().get(column), booleanValue(ctx));
    }

    /** True when the column is set, false when it is null, for the has-a flags. */
    public static <T> Predicate isSet(FilterPredicateContext<T> ctx, String column) {
        return booleanValue(ctx)
                ? ctx.cb().isNotNull(ctx.root().get(column))
                : ctx.cb().isNull(ctx.root().get(column));
    }

    public static <T> boolean booleanValue(FilterPredicateContext<T> ctx) {
        Object first = ctx.firstValue();
        return !(first instanceof Boolean b) || b;
    }

    // ─── LIKE escaping ────────────────────────────────────────────────────────

    public static final char ESCAPE = '\\';

    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
