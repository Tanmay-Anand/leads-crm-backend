package com.leadrat.crm.leads.api.user.dto;

import java.util.UUID;

/** Feeds the lead assignee dropdown and any other "pick a person" control. */
public record UserNamesDto(UUID id, String displayName) {
}
