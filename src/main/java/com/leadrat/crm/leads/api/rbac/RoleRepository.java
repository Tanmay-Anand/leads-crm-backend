package com.leadrat.crm.leads.api.rbac;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends TenantAwareRepository<Role> {

    boolean existsByTenantAndNameIgnoreCase(UUID tenant, String name);

    boolean existsByTenantAndNameIgnoreCaseAndIdNot(UUID tenant, String name, UUID excludeId);

    Optional<Role> findByTenantAndNameIgnoreCase(UUID tenant, String name);

    /** Tenant-qualified on purpose - see {@code UserRepository.findByIdAndTenant}'s doc. */
    Optional<Role> findByIdAndTenant(UUID id, UUID tenant);
}
