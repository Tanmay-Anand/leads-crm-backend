package com.leadrat.crm.leads.api.search.advanced;

import com.leadrat.crm.leads.api.exception.LeadratException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Turns the wire format (everything is a string) into the typed values the predicates need.
 *
 * <p>A value that will not coerce is a 400 rather than a silently dropped criterion, so a
 * malformed filter never looks like a filter that simply matched nothing.
 */
public final class FilterValueCoercer {

    private FilterValueCoercer() {
    }

    public static List<Object> coerce(FilterValueType type, List<String> rawValues) {
        if (rawValues == null) {
            return List.of();
        }
        return rawValues.stream()
                .map(v -> coerceOne(type, v))
                .toList();
    }

    private static Object coerceOne(FilterValueType type, String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return switch (type) {
                case STRING, ENUM -> raw;
                case NUMBER -> Long.parseLong(raw.trim());
                case DECIMAL -> new BigDecimal(raw.trim());
                case BOOLEAN -> Boolean.parseBoolean(raw.trim());
                case DATE -> LocalDate.parse(raw.trim());
                case DATETIME -> LocalDateTime.parse(raw.trim());
                case UUID, LOOKUP, ID_SET -> UUID.fromString(raw.trim());
            };
        } catch (Exception e) {
            throw new LeadratException(
                    "Invalid value " + raw + " for filter type " + type + ": " + e.getMessage(),
                    HttpStatus.BAD_REQUEST);
        }
    }
}
