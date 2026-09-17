package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.lead.dto.LeadNoteDto;
import com.leadrat.crm.leads.api.lead.dto.LeadNoteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LeadNoteService {

    Page<LeadNoteDto> getByLead(UUID leadId, Pageable pageable);

    LeadNoteDto add(UUID leadId, LeadNoteRequest request);

    LeadNoteDto update(UUID leadId, UUID noteId, LeadNoteRequest request);

    void delete(UUID leadId, UUID noteId);
}
