package com.leadrat.crm.leads.api.user;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds the equality predicates behind {@link UserListFilters}. Relies on the ambient Hibernate
 *  tenant filter for tenant scoping, same as {@link UserSimpleSearchSpecification} - both only
 *  ever run inside a {@code @Transactional} service method. */
final class UserFilterSpecifications {

    private UserFilterSpecifications() {
    }

    static Specification<User> matching(UserListFilters filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filters.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), filters.enabled()));
            }
            if (filters.role() != null) {
                predicates.add(cb.equal(root.get("role"), filters.role()));
            }
            if (filters.customRoleId() != null) {
                predicates.add(cb.equal(root.get("customRoleId"), filters.customRoleId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
