package com.leadrat.crm.leads.api.lead.dto;

/** Answer to GET /leads/check-mobile. The lead is populated only when exists is true. */
public record DuplicateCheckResponse(boolean exists, LeadDto lead) {

    public static DuplicateCheckResponse notFound() {
        return new DuplicateCheckResponse(false, null);
    }

    public static DuplicateCheckResponse found(LeadDto lead) {
        return new DuplicateCheckResponse(true, lead);
    }
}
