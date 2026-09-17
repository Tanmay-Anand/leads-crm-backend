package com.leadrat.crm.leads.api.leadstatus.dto;

/** Partial update: a null field is left untouched. */
public record LeadStatusUpdateRequest(
        String name,
        String displayName,
        String colorCode,
        Boolean isDefault,
        Boolean isNoteRequired,
        Integer displayOrder
) {
}
