package com.leadrat.crm.leads.api.project;

import com.leadrat.crm.leads.api.core.EnumDto;
import com.leadrat.crm.leads.api.search.SearchResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects")
@Tag(name = "Projects", description = "Real estate project management")
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Get projects with pagination",
            description = "Supports free-text search via q (minimum 2 characters) and a date range via fromDate "
                    + "and toDate. Text search and date range compose freely.")
    @GetMapping
    public ResponseEntity<Page<ProjectDto>> getAll(
            @Parameter(description = "Free-text search term, minimum 2 characters.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Comma-separated field names to search. Omit to search all fields.")
            @RequestParam(required = false) List<ProjectSearchField> searchFields,
            @Parameter(description = "Inclusive lower bound on created, as yyyy-MM-dd.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Inclusive upper bound on created, as yyyy-MM-dd.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable) {

        if (q != null && q.trim().length() >= 2) {
            return ResponseEntity.ok(
                    projectService.simpleSearch(q.trim(), searchFields, pageable, fromDate, toDate));
        }
        return ResponseEntity.ok(projectService.getAll(pageable, fromDate, toDate));
    }

    @Operation(summary = "Search projects with structured filters")
    @PostMapping("/search")
    public ResponseEntity<Page<ProjectDto>> search(
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestBody @Valid List<SearchResource> resources) {
        return ResponseEntity.ok(projectService.search(resources, pageable));
    }

    @Operation(summary = "Get all projects as id and name pairs",
            description = "Feeds the project picker on the lead form and the project filter dropdown.")
    @GetMapping("/names")
    public ResponseEntity<List<ProjectNamesDto>> getNames() {
        return ResponseEntity.ok(projectService.getNames());
    }

    @Operation(summary = "Get project counts for the list header")
    @GetMapping("/stats")
    public ResponseEntity<ProjectStatsDto> getStats() {
        return ResponseEntity.ok(projectService.getStats());
    }

    @Operation(summary = "Get the enum options the project form needs")
    @GetMapping("/enums")
    public ResponseEntity<Map<String, List<EnumDto>>> getEnums() {
        return ResponseEntity.ok(Map.of(
                "projectStages", Arrays.stream(ProjectStage.values()).map(EnumDto::of).toList(),
                "projectTypes", Arrays.stream(ProjectType.values()).map(EnumDto::of).toList(),
                "areaUnits", Arrays.stream(AreaUnit.values()).map(EnumDto::of).toList()));
    }

    @Operation(summary = "Get a project by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.get(id));
    }

    @Operation(summary = "Create a new project")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created"),
            @ApiResponse(responseCode = "409", description = "A project with that name already exists")
    })
    @PostMapping
    public ResponseEntity<ProjectDto> save(@Valid @RequestBody ProjectDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.save(request));
    }

    @Operation(summary = "Update an existing project")
    @PutMapping("/{id}")
    public ResponseEntity<ProjectDto> edit(@PathVariable UUID id, @Valid @RequestBody ProjectDto request) {
        return ResponseEntity.ok(projectService.edit(id, request));
    }

    @Operation(summary = "Soft-delete a project",
            description = "Refused with 409 while active leads still reference the project.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
