package com.leadrat.crm.leads.api.project;

import com.leadrat.crm.leads.api.search.SearchResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProjectService {

    Page<ProjectDto> getAll(Pageable pageable, LocalDate fromDate, LocalDate toDate);

    Page<ProjectDto> simpleSearch(String query, List<ProjectSearchField> searchFields, Pageable pageable,
                                  LocalDate fromDate, LocalDate toDate);

    Page<ProjectDto> search(List<SearchResource> resources, Pageable pageable);

    /** Id and name pairs for project pickers. */
    List<ProjectNamesDto> getNames();

    ProjectStatsDto getStats();

    ProjectDto get(UUID id);

    ProjectDto save(ProjectDto request);

    ProjectDto edit(UUID id, ProjectDto request);

    void delete(UUID id);
}
