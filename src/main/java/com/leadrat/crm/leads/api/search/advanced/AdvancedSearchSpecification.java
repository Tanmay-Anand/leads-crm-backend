package com.leadrat.crm.leads.api.search.advanced;

import com.leadrat.crm.leads.api.exception.LeadratException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AdvancedSearchSpecification {

    private static final Logger log = LoggerFactory.getLogger(AdvancedSearchSpecification.class);

    private AdvancedSearchSpecification() {
    }

    /**
     * Turns the criteria into one ANDed specification.
     *
     * <p>rejectUnknown true, the default for an interactive search, surfaces an unknown field, a
     * disallowed operator or a malformed value as a 400. False skips the offending criterion
     * instead, which is the mode a saved filter is replayed in, so a filter naming a field that has
     * since been removed degrades to a narrower search rather than a broken page.
     */
    public static <T, F extends Enum<F> & FilterFieldSpec> Specification<T> build(
            AdvancedSearchRequest request,
            FilterFieldRegistry<T, F> registry,
            UUID tenantId,
            boolean rejectUnknown) {

        Specification<T> spec = Specification.allOf();

        if (request.criteria() == null || request.criteria().isEmpty()) {
            return spec;
        }

        List<String> skipped = new ArrayList<>();

        for (FilterCriterion criterion : request.criteria()) {
            try {
                F field = registry.lookup(criterion.field());
                registry.assertOperatorAllowed(field, criterion.operator());
                List<Object> coerced = FilterValueCoercer.coerce(field.valueType(), criterion.values());
                spec = spec.and(registry.specFor(field, criterion.operator(), coerced, tenantId));
            } catch (LeadratException e) {
                if (rejectUnknown) {
                    throw e;
                }
                skipped.add(criterion.field());
            }
        }

        if (!skipped.isEmpty()) {
            log.warn("Advanced search skipped {} unusable criteria: {}", skipped.size(), skipped);
        }

        return spec;
    }
}
