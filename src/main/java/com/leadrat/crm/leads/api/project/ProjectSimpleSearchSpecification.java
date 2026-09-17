package com.leadrat.crm.leads.api.project;

import com.leadrat.crm.leads.api.search.SearchUtils;
import com.leadrat.crm.leads.api.search.SimpleSearchSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Arrays;
import java.util.List;

/** Free-text search across the project list. */
public class ProjectSimpleSearchSpecification extends SimpleSearchSpecification<Project, ProjectSearchField> {

    public ProjectSimpleSearchSpecification(String query, List<ProjectSearchField> searchFields) {
        super(query, searchFields);
    }

    @Override
    protected ProjectSearchField[] allFields() {
        return ProjectSearchField.values();
    }

    @Override
    protected Predicate buildPredicate(Root<Project> root, CriteriaQuery<?> cq,
                                       CriteriaBuilder cb, ProjectSearchField field, String pattern) {
        return switch (field) {
            case CITY -> like(cb, root.get("address").get("city"), pattern);
            case STATE -> like(cb, root.get("address").get("state"), pattern);
            case PROJECT_TYPE -> enumIn(root, "projectType", ProjectType.values(), pattern);
            case PROJECT_STAGE -> enumIn(root, "projectStage", ProjectStage.values(), pattern);
            default -> field.getFieldName() == null
                    ? null
                    : like(cb, root.get(field.getFieldName()), pattern);
        };
    }

    private Predicate like(CriteriaBuilder cb, Path<String> path, String pattern) {
        return cb.like(cb.lower(path), pattern, SearchUtils.ESCAPE);
    }

    /**
     * Matches an enum column by name, tolerating the spacing a user types: pre-launch finds
     * PRE_LAUNCH.
     */
    private <E extends Enum<E>> Predicate enumIn(Root<Project> root, String column, E[] values, String pattern) {
        String term = pattern.replace("%", "").replace(" ", "_").replace("-", "_").toLowerCase();
        if (term.isBlank()) {
            return null;
        }
        List<E> matches = Arrays.stream(values)
                .filter(v -> v.name().toLowerCase().contains(term))
                .toList();
        return matches.isEmpty() ? null : root.get(column).in(matches);
    }
}
