package com.leadrat.crm.leads.api.project;

import java.util.UUID;

/** Lightweight pair for project pickers and the lead filter dropdown. */
public record ProjectNamesDto(UUID id, String name) {
}
