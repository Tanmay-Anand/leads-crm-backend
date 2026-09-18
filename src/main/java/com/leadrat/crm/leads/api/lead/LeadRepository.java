package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends TenantAwareRepository<Lead> {

    Page<Lead> findByTenant(UUID tenant, Pageable pageable);

    List<Lead> findByTenantAndIsActiveTrue(UUID tenant);

    /**
     * Resolves an incoming submission to the one lead that owns its identity.
     *
     * <p>The uniqueness key is {@code (tenant, project_id, mobile_normalized)}: the same person may
     * exist once per project, each potentially held by a different channel partner. Tenant-scoped
     * explicitly rather than trusting the ambient Hibernate filter, because this decides whether a
     * write is a create or a conflict.
     */
    Optional<Lead> findFirstByTenantAndProjectIdAndMobileNormalizedAndIsActiveTrue(
            UUID tenant, UUID projectId, String mobileNormalized);

    /**
     * The project-less counterpart of the finder above.
     *
     * <p>Needed as its own method because SQL compares NULL with IS NULL, not with equals, so
     * passing a null projectId to the two-argument finder would match nothing.
     */
    Optional<Lead> findFirstByTenantAndProjectIdIsNullAndMobileNormalizedAndIsActiveTrue(
            UUID tenant, String mobileNormalized);

    Optional<Lead> findByTenantAndLeadCode(UUID tenant, String leadCode);

    long countByTenantAndIsActiveTrue(UUID tenant);

    long countByTenantAndStatusIdAndIsActiveTrue(UUID tenant, UUID statusId);

    long countByTenantAndIsActiveTrueAndCreatedGreaterThanEqual(UUID tenant, LocalDateTime since);

    long countByTenantAndProjectIdAndIsActiveTrue(UUID tenant, UUID projectId);

    long countByTenantAndChannelPartnerIdAndIsActiveTrue(UUID tenant, UUID channelPartnerId);

    boolean existsByTenantAndProjectIdAndIsActiveTrue(UUID tenant, UUID projectId);

    boolean existsByTenantAndChannelPartnerIdAndIsActiveTrue(UUID tenant, UUID channelPartnerId);

    // ─── Status migration ─────────────────────────────────────────────────────
    // Used when a status is removed while leads still reference it: the leads are moved in bulk to
    // the replacement the admin picked. Tenant is explicit because a JPQL query does not go through
    // the Hibernate tenant filter.

    @Query("SELECT l FROM Lead l WHERE l.tenant = :tenant AND l.statusId = :statusId AND l.isActive = true")
    List<Lead> findActiveByTenantAndStatusId(@Param("tenant") UUID tenant, @Param("statusId") UUID statusId);

    /**
     * Distinct channel partners appearing on this tenant leads, as id and name pairs.
     *
     * <p>Drives the CHANNEL_PARTNER filter dropdown. Read from the denormalised columns rather than
     * the channel partner table, because a partner who has never been attributed a lead is not a
     * useful filter value.
     */
    @Query("""
            SELECT DISTINCT l.channelPartnerId, l.channelPartnerName FROM Lead l
            WHERE l.tenant = :tenant AND l.isActive = true
              AND l.channelPartnerId IS NOT NULL AND l.channelPartnerName IS NOT NULL
            ORDER BY l.channelPartnerName
            """)
    List<Object[]> findDistinctChannelPartners(@Param("tenant") UUID tenant);
}
