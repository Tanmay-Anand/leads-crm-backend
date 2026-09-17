package com.leadrat.crm.leads.api.project;

/** KPI tiles above the project list. */
public record ProjectStatsDto(
        long total,
        long drafts,
        long launched,
        long delivered
) {
}
