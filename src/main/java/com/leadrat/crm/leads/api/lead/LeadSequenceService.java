package com.leadrat.crm.leads.api.lead;

import java.util.UUID;

public interface LeadSequenceService {

    /** Next code for the tenant, in the form LD-NNNNNN. */
    String nextCode(UUID tenantId);
}
