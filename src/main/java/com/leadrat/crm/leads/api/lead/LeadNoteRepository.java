package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface LeadNoteRepository extends TenantAwareRepository<LeadNote> {

    Page<LeadNote> findByTenantAndLeadIdAndIsActiveTrue(UUID tenant, UUID leadId, Pageable pageable);

    List<LeadNote> findByTenantAndLeadIdAndIsActiveTrueOrderByCreatedDesc(UUID tenant, UUID leadId);

    long countByTenantAndLeadIdAndIsActiveTrue(UUID tenant, UUID leadId);
}
