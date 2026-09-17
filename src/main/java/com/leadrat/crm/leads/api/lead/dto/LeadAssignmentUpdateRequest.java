package com.leadrat.crm.leads.api.lead.dto;

import com.leadrat.crm.leads.api.lead.AssignmentMethod;

import java.util.UUID;

/**
 * Reassigns a lead.
 *
 * <p>The display name travels with the id because the lead denormalises it; there is no user table
 * here to resolve it from later.
 */
public record LeadAssignmentUpdateRequest(
        UUID assignedTo,
        String assignedToUserName,
        UUID telecallerId,
        String telecallerName,
        AssignmentMethod assignmentMethod
) {
}
