package com.leadrat.crm.leads.api.channelpartner;

/** KPI tiles above the channel partner list. */
public record ChannelPartnerStatsDto(
        long total,
        long active,
        long drafts,
        long platinum
) {
}
