package com.leadrat.crm.leads.api.lead.dto;

import java.util.List;

/** KPI tiles above the lead list. */
public record LeadSummaryDto(
        long total,
        long createdThisMonth,
        long unassigned,
        long dueToday,
        List<StatusCount> byStatus
) {
    public record StatusCount(String statusId, String statusName, String colorCode, long count) {
    }
}
