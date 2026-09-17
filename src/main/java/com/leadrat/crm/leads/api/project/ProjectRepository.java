package com.leadrat.crm.leads.api.project;

import com.leadrat.crm.leads.api.core.SaveStatus;
import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends TenantAwareRepository<Project> {

    Page<Project> findByTenant(UUID tenant, Pageable pageable);

    List<Project> findByTenantAndIsActiveTrueOrderByNameAsc(UUID tenant);

    Optional<Project> findFirstByTenantAndNameIgnoreCaseAndIsActiveTrue(UUID tenant, String name);

    boolean existsByTenantAndNameIgnoreCaseAndIsActiveTrueAndIdNot(UUID tenant, String name, UUID excludeId);

    long countByTenantAndIsActiveTrue(UUID tenant);

    long countByTenantAndSaveStatusAndIsActiveTrue(UUID tenant, SaveStatus saveStatus);

    long countByTenantAndProjectStageAndIsActiveTrue(UUID tenant, ProjectStage stage);
}
