package com.leadrat.crm.leads.api.tag;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomTagRepository extends TenantAwareRepository<CustomTag> {

    List<CustomTag> findByTenantAndIsActiveTrueOrderByNameAsc(UUID tenant);

    List<CustomTag> findByTenantAndIdInAndIsActiveTrue(UUID tenant, Collection<UUID> ids);

    Optional<CustomTag> findFirstByTenantAndNameAndIsActiveTrue(UUID tenant, String name);
}
