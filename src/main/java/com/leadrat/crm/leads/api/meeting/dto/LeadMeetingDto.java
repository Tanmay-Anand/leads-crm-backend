package com.leadrat.crm.leads.api.meeting.dto;

import com.leadrat.crm.leads.api.meeting.LeadMeeting;

import java.time.LocalDateTime;
import java.util.UUID;

public record LeadMeetingDto(
        UUID id,
        UUID leadId,
        String title,
        String agenda,
        LocalDateTime scheduledAt,
        int durationMinutes,
        String timezone,
        String meetingLink,
        String status,
        String calendarSyncStatus,
        String calendarSyncError,
        String recallBotStatus,
        UUID assignedUserId,
        String assignedUserEmail,
        String assignedUserName) {

    public static LeadMeetingDto from(LeadMeeting meeting) {
        return new LeadMeetingDto(meeting.getId(), meeting.getLeadId(), meeting.getTitle(), meeting.getAgenda(),
                meeting.getScheduledAt(), meeting.getDurationMinutes(), meeting.getTimezone(),
                meeting.getMeetingLink(), meeting.getStatus(), meeting.getCalendarSyncStatus(),
                meeting.getCalendarSyncError(), meeting.getRecallBotStatus(), meeting.getAssignedUserId(),
                meeting.getAssignedUserEmail(), meeting.getAssignedUserName());
    }
}
