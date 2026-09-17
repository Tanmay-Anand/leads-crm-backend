package com.leadrat.crm.leads.api.tenant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.UUID;

/**
 * Base repository for tenant-aware entities.
 *
 * <p>Extending this is what gets a repository two things: the Hibernate tenant filter, applied
 * by {@link TenantFilterAspect} on any call to a subtype, and soft delete, via
 * {@code TenantAwareRepositoryImpl}. Bulk deletes throw.
 */
@NoRepositoryBean
public interface TenantAwareRepository<T extends TenantAwareAggregateRoot<T>>
        extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T> {

    List<T> findByTenant(UUID tenant);

    List<T> findByTenant(UUID tenant, Sort sort);

    Page<T> findByTenant(UUID tenant, Pageable pageable);
}
