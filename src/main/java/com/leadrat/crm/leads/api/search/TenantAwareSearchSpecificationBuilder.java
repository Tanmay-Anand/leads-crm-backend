package com.leadrat.crm.leads.api.search;

import com.leadrat.crm.leads.api.tenant.TenantAwareAggregateRoot;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds an ANDed specification from posted {@link SearchResource}s, with the tenant added as one
 * more criterion.
 *
 * <p>The Hibernate filter already scopes the query; naming the tenant here too means a specification
 * built for one tenant cannot be reused against another by accident.
 */
public class TenantAwareSearchSpecificationBuilder<T extends TenantAwareAggregateRoot<?>> {

    private final List<SearchResource> specs = new ArrayList<>();

    public TenantAwareSearchSpecificationBuilder(List<SearchResource> resources, UUID tenantId) {
        if (resources != null) {
            this.specs.addAll(resources);
        }
        if (tenantId != null) {
            this.specs.add(new SearchResource("tenant", tenantId.toString(), "UUID"));
        }
    }

    public Specification<T> build() {
        if (specs.isEmpty()) {
            return Specification.allOf();
        }

        List<Specification<T>> parts = specs.stream()
                .filter(Objects::nonNull)
                .map((SearchResource r) -> (Specification<T>) new SearchSpecification<T>(r))
                .toList();

        if (parts.isEmpty()) {
            return Specification.allOf();
        }

        @SuppressWarnings("unchecked")
        Specification<T>[] array = parts.toArray(new Specification[0]);
        return Specification.allOf(array);
    }

    public List<SearchResource> getSpecs() {
        return specs;
    }
}
