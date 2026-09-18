package com.leadrat.crm.leads.api.user;

import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends TenantAwareRepository<User> {

    List<User> findByTenantAndIsActiveTrueOrderByFirstNameAscLastNameAsc(UUID tenant);

    /** Tenant-qualified on purpose, not just {@code findById}: correctness of a permission check
     *  must not depend on {@code TenantFilterAspect}'s Hibernate-session-filter timing, which is
     *  not guaranteed relative to a {@code @PreAuthorize} evaluation (see {@code
     *  EffectivePermissionLoader}'s class doc). */
    Optional<User> findByIdAndTenant(UUID id, UUID tenant);

    long countByTenantAndCustomRoleIdAndIsActiveTrue(UUID tenant, UUID customRoleId);

    long countByTenantAndRoleAndEnabledTrueAndIsActiveTrue(UUID tenant, UserRole role);

    /**
     * Bypasses both {@code @SQLRestriction("is_active = true")} and the Hibernate
     * {@code tenantFilter} - {@code findById} is hidden by the former, which would turn a
     * legitimate 403/404 into a raw constraint-violation 500 on the provisioning and
     * reactivate-in-place write paths (see {@code UserServiceImpl}/{@code
     * UserProvisioningService}). Callers are responsible for their own tenant check on the
     * result - this method deliberately does not filter by tenant, since JIT provisioning must
     * find a user regardless of which tenant's context happens to be active when it runs.
     */
    @Query(value = "select * from crm.\"user\" where id = :id", nativeQuery = true)
    Optional<User> findAnyById(@Param("id") UUID id);

    @Query(value = "select * from crm.\"user\" where tenant = :tenant and lower(email) = lower(:email)",
            nativeQuery = true)
    Optional<User> findAnyByTenantAndEmail(@Param("tenant") UUID tenant, @Param("email") String email);
}
