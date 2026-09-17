package com.leadrat.crm.leads.api.leadstatus;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadStatusRepository extends TenantAwareRepository<LeadStatus> {

    List<LeadStatus> findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(UUID tenant);

    Optional<LeadStatus> findFirstByTenantAndIsDefaultTrueAndIsActiveTrue(UUID tenant);

    Optional<LeadStatus> findFirstByTenantAndNameAndIsActiveTrue(UUID tenant, String name);

    boolean existsByTenantAndIsDefaultTrueAndIsActiveTrueAndIdNot(UUID tenant, UUID excludeId);

    long countByTenantAndIsActiveTrue(UUID tenant);
}
