package com.leadrat.crm.leads.api.channelpartner;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelPartnerRepository extends TenantAwareRepository<ChannelPartner> {

    Page<ChannelPartner> findByTenant(UUID tenant, Pageable pageable);

    List<ChannelPartner> findByTenantAndIsActiveTrueOrderByNameAsc(UUID tenant);

    Optional<ChannelPartner> findFirstByTenantAndEmailIgnoreCaseAndIsActiveTrue(UUID tenant, String email);

    boolean existsByTenantAndEmailIgnoreCaseAndIsActiveTrueAndIdNot(UUID tenant, String email, UUID excludeId);

    long countByTenantAndIsActiveTrue(UUID tenant);

    long countByTenantAndOnboardingStatusAndIsActiveTrue(UUID tenant, ChannelPartnerOnboardingStatus status);

    long countByTenantAndTierAndIsActiveTrue(UUID tenant, ChannelPartnerTier tier);
}
