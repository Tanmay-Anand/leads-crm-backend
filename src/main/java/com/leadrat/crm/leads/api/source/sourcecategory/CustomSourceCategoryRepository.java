package com.leadrat.crm.leads.api.source.sourcecategory;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomSourceCategoryRepository extends TenantAwareRepository<CustomSourceCategory> {

    List<CustomSourceCategory> findByTenantAndIsActiveTrueOrderByNameAsc(UUID tenant);

    Optional<CustomSourceCategory> findFirstByTenantAndNameAndIsActiveTrue(UUID tenant, String name);
}
