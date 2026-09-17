package com.leadrat.crm.leads.api.project;

import com.leadrat.crm.leads.api.core.SaveStatus;
import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.lead.LeadRepository;
import com.leadrat.crm.leads.api.search.SearchResource;
import com.leadrat.crm.leads.api.search.TenantAwareSearchSpecificationBuilder;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import com.leadrat.crm.leads.api.util.Validations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final LeadRepository leadRepository;
    private final TenantAware tenantAware;

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectDto> getAll(Pageable pageable, LocalDate fromDate, LocalDate toDate) {
        UUID tenantId = requireTenant();
        Specification<Project> spec = dateRangeSpec(fromDate, toDate);
        Page<Project> page = spec == null
                ? projectRepository.findByTenant(tenantId, pageable)
                : projectRepository.findAll(spec, pageable);
        return page.map(project -> toDto(project, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectDto> simpleSearch(String query, List<ProjectSearchField> searchFields, Pageable pageable,
                                         LocalDate fromDate, LocalDate toDate) {
        UUID tenantId = requireTenant();
        Specification<Project> spec = new ProjectSimpleSearchSpecification(query, searchFields);

        Specification<Project> dateSpec = dateRangeSpec(fromDate, toDate);
        if (dateSpec != null) {
            spec = spec.and(dateSpec);
        }

        return projectRepository.findAll(spec, pageable).map(project -> toDto(project, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectDto> search(List<SearchResource> resources, Pageable pageable) {
        UUID tenantId = requireTenant();
        Specification<Project> spec =
                new TenantAwareSearchSpecificationBuilder<Project>(resources, tenantId).build();
        return projectRepository.findAll(spec, pageable).map(project -> toDto(project, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectNamesDto> getNames() {
        return projectRepository.findByTenantAndIsActiveTrueOrderByNameAsc(requireTenant()).stream()
                .map(p -> new ProjectNamesDto(p.getId(), p.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectStatsDto getStats() {
        UUID tenantId = requireTenant();
        return new ProjectStatsDto(
                projectRepository.countByTenantAndIsActiveTrue(tenantId),
                projectRepository.countByTenantAndSaveStatusAndIsActiveTrue(tenantId, SaveStatus.DRAFT),
                projectRepository.countByTenantAndProjectStageAndIsActiveTrue(tenantId, ProjectStage.LAUNCHED),
                projectRepository.countByTenantAndProjectStageAndIsActiveTrue(tenantId, ProjectStage.DELIVERED));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDto get(UUID id) {
        UUID tenantId = requireTenant();
        return toDto(requireProject(id), tenantId);
    }

    @Override
    @Transactional
    public ProjectDto save(ProjectDto request) {
        UUID tenantId = requireTenant();
        Validations.isValidRera(request.reraNumber());

        projectRepository.findFirstByTenantAndNameIgnoreCaseAndIsActiveTrue(tenantId, request.name())
                .ifPresent(existing -> {
                    throw new LeadratException("A project named " + request.name() + " already exists.",
                            HttpStatus.CONFLICT);
                });

        Project project = request.toEntity();
        project.tenant(tenantId);
        Project saved = projectRepository.save(project);
        log.info("Created project {} for tenant {}", saved.getId(), tenantId);
        return toDto(saved, tenantId);
    }

    @Override
    @Transactional
    public ProjectDto edit(UUID id, ProjectDto request) {
        UUID tenantId = requireTenant();
        Project project = requireProject(id);
        Validations.isValidRera(request.reraNumber());

        if (projectRepository.existsByTenantAndNameIgnoreCaseAndIsActiveTrueAndIdNot(
                tenantId, request.name(), id)) {
            throw new LeadratException("A project named " + request.name() + " already exists.",
                    HttpStatus.CONFLICT);
        }

        request.applyTo(project);
        return toDto(projectRepository.save(project), tenantId);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = requireTenant();
        Project project = requireProject(id);

        // Leads hold a projectId with no FK, so nothing at the database level would stop this.
        // Refuse rather than orphan them: the alternative is a lead pointing at a project the UI
        // can no longer resolve a name for.
        if (leadRepository.existsByTenantAndProjectIdAndIsActiveTrue(tenantId, id)) {
            long count = leadRepository.countByTenantAndProjectIdAndIsActiveTrue(tenantId, id);
            throw new LeadratException(
                    count + " active leads reference this project. Reassign them before deleting it.",
                    HttpStatus.CONFLICT);
        }

        projectRepository.delete(project);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private ProjectDto toDto(Project project, UUID tenantId) {
        return ProjectDto.fromEntity(project,
                leadRepository.countByTenantAndProjectIdAndIsActiveTrue(tenantId, project.getId()));
    }

    private Project requireProject(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + id));
        tenantAware.validate(project);
        return project;
    }

    /**
     * Inclusive range over the created timestamp. Returns null when neither bound was supplied, so
     * callers can skip composing a no-op specification.
     */
    private Specification<Project> dateRangeSpec(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return null;
        }
        return (root, cq, cb) -> {
            if (fromDate != null && toDate != null) {
                return cb.between(root.get("created"), fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX));
            }
            if (fromDate != null) {
                return cb.greaterThanOrEqualTo(root.get("created"), fromDate.atStartOfDay());
            }
            return cb.lessThanOrEqualTo(root.get("created"), toDate.atTime(LocalTime.MAX));
        };
    }

    private UUID requireTenant() {
        UUID tenantId = tenantAware.getTenantId();
        if (tenantId == null) {
            throw new LeadratException("Tenant context is required for this operation",
                    HttpStatus.PRECONDITION_FAILED);
        }
        return tenantId;
    }
}
