package com.leadrat.crm.leads.api.leadstatus;

import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusReorderRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusUpdateRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusUsageDto;
import com.leadrat.crm.leads.api.search.SearchResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface LeadStatusService {

    Page<LeadStatus> getAll(Pageable pageable);

    Page<LeadStatus> search(List<SearchResource> resources, Pageable pageable);

    LeadStatus getById(UUID id);

    /** Ordered, unpaginated list for pickers. */
    List<LeadStatus> getNames();

    LeadStatus add(LeadStatusRequest request);

    LeadStatus update(UUID id, LeadStatusUpdateRequest request);

    List<LeadStatus> reorder(LeadStatusReorderRequest request);

    LeadStatusUsageDto usage(UUID id);

    /** Soft-deletes the status. Active leads on it are moved to targetStatusId, required if any. */
    void delete(UUID id, UUID targetStatusId);
}
