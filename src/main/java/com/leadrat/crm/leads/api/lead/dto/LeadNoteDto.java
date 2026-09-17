package com.leadrat.crm.leads.api.lead.dto;

import com.leadrat.crm.leads.api.lead.LeadNote;
import com.leadrat.crm.leads.api.lead.LeadNoteType;

import java.time.LocalDateTime;
import java.util.UUID;

public record LeadNoteDto(
        UUID id,
        UUID leadId,
        LeadNoteType type,
        String body,
        String externalReference,
        UUID performedByUserId,
        String performedByUsername,
        LocalDateTime createdOn
) {
    public static LeadNoteDto fromEntity(LeadNote note) {
        return new LeadNoteDto(
                note.getId(),
                note.getLeadId(),
                note.getType(),
                note.getBody(),
                note.getExternalReference(),
                note.getPerformedByUserId(),
                note.getPerformedByUsername(),
                note.getCreated());
    }
}
