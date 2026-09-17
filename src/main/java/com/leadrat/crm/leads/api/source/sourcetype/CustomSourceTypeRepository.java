package com.leadrat.crm.leads.api.source.sourcetype;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomSourceTypeRepository extends TenantAwareRepository<CustomSourceType> {

    List<CustomSourceType> findByTenantAndIsActiveTrueOrderByNameAsc(UUID tenant);

    List<CustomSourceType> findByTenantAndParentIdAndIsActiveTrueOrderByNameAsc(UUID tenant, UUID parentId);

    Optional<CustomSourceType> findFirstByTenantAndNameAndIsActiveTrue(UUID tenant, String name);
}
