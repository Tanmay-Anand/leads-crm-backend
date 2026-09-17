package com.leadrat.crm.leads.api.leadstatus.dto;

import java.util.UUID;

/** How many active leads sit on a status, so the UI can warn before a delete. */
public record LeadStatusUsageDto(UUID statusId, long activeLeadCount) {
}
