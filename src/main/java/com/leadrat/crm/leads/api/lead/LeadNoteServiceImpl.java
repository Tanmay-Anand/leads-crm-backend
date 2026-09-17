package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.lead.dto.LeadNoteDto;
import com.leadrat.crm.leads.api.lead.dto.LeadNoteRequest;
import com.leadrat.crm.leads.api.seeding.TenantSeedingService;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadNoteServiceImpl implements LeadNoteService {

    private final LeadNoteRepository leadNoteRepository;
    private final LeadRepository leadRepository;
    private final TenantAware tenantAware;

    @Override
    @Transactional(readOnly = true)
    public Page<LeadNoteDto> getByLead(UUID leadId, Pageable pageable) {
        UUID tenantId = requireTenant();
        requireLead(leadId);
        return leadNoteRepository.findByTenantAndLeadIdAndIsActiveTrue(tenantId, leadId, pageable)
                .map(LeadNoteDto::fromEntity);
    }

    @Override
    @Transactional
    public LeadNoteDto add(UUID leadId, LeadNoteRequest request) {
        UUID tenantId = requireTenant();
        requireLead(leadId);

        LeadNote note = new LeadNote();
        note.tenant(tenantId);
        note.setLeadId(leadId);
        note.setType(request.type());
        note.setBody(request.body());
        note.setExternalReference(request.externalReference());
        note.setPerformedByUserId(currentUserId());
        note.setPerformedByUsername(tenantAware.getLoggedInUsername());

        return LeadNoteDto.fromEntity(leadNoteRepository.save(note));
    }

    @Override
    @Transactional
    public LeadNoteDto update(UUID leadId, UUID noteId, LeadNoteRequest request) {
        LeadNote note = requireNote(leadId, noteId);
        note.setType(request.type());
        note.setBody(request.body());
        note.setExternalReference(request.externalReference());
        return LeadNoteDto.fromEntity(leadNoteRepository.save(note));
    }

    @Override
    @Transactional
    public void delete(UUID leadId, UUID noteId) {
        leadNoteRepository.delete(requireNote(leadId, noteId));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Lead requireLead(UUID leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new EntityNotFoundException("Lead not found: " + leadId));
        tenantAware.validate(lead);
        return lead;
    }

    /**
     * Loads a note and checks it really belongs to the lead in the path.
     *
     * <p>Reports 404 rather than 400 on a mismatch: a note that is not this lead note is, from the
     * caller point of view, not found at that address.
     */
    private LeadNote requireNote(UUID leadId, UUID noteId) {
        requireLead(leadId);
        LeadNote note = leadNoteRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException("Lead note not found: " + noteId));
        tenantAware.validate(note);
        if (!leadId.equals(note.getLeadId())) {
            throw new EntityNotFoundException("Lead note not found: " + noteId);
        }
        return note;
    }

    private UUID requireTenant() {
        UUID tenantId = tenantAware.getTenantId();
        if (tenantId == null) {
            throw new LeadratException("Tenant context is required for this operation",
                    HttpStatus.PRECONDITION_FAILED);
        }
        return tenantId;
    }

    private UUID currentUserId() {
        UUID userId = tenantAware.getLoggedInUserId();
        return userId != null ? userId : TenantSeedingService.SYSTEM_USER;
    }
}
