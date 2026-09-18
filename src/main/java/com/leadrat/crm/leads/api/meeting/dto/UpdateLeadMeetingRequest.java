package com.leadrat.crm.leads.api.meeting.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateLeadMeetingRequest(
        String title,
        String agenda,
        LocalDateTime scheduledAt,
        Integer durationMinutes,
        String timezone,
        UUID assignedUserId) {
}
