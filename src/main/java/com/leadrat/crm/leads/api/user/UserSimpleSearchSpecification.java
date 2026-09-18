package com.leadrat.crm.leads.api.user;

import com.leadrat.crm.leads.api.search.SearchUtils;
import com.leadrat.crm.leads.api.search.SimpleSearchSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.List;

/** Free-text search across the user list. */
public class UserSimpleSearchSpecification extends SimpleSearchSpecification<User, UserSearchField> {

    public UserSimpleSearchSpecification(String query, List<UserSearchField> searchFields) {
        super(query, searchFields);
    }

    @Override
    protected UserSearchField[] allFields() {
        return UserSearchField.values();
    }

    @Override
    protected Predicate buildPredicate(Root<User> root, CriteriaQuery<?> cq,
                                        CriteriaBuilder cb, UserSearchField field, String pattern) {
        return like(cb, root.get(field.getFieldName()), pattern);
    }

    private Predicate like(CriteriaBuilder cb, Path<String> path, String pattern) {
        return cb.like(cb.lower(path), pattern, SearchUtils.ESCAPE);
    }
}
