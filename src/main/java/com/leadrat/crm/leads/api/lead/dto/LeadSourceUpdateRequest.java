package com.leadrat.crm.leads.api.lead.dto;

import java.util.UUID;

/** Both halves of the source taxonomy travel together, since a type is meaningless without its category. */
public record LeadSourceUpdateRequest(
        UUID sourceCategoryId,
        UUID sourceTypeId
) {
}
