package com.leadrat.crm.leads.api.meeting;

import com.leadrat.aisdk.meeting.Attendee;
import com.leadrat.aisdk.meeting.Meeting;
import com.leadrat.aisdk.meeting.MeetingService;
import com.leadrat.aisdk.meeting.dto.CreateMeetingRequest;
import com.leadrat.aisdk.meeting.dto.UpdateMeetingRequest;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.lead.Lead;
import com.leadrat.crm.leads.api.lead.LeadRepository;
import com.leadrat.crm.leads.api.meeting.dto.CreateLeadMeetingRequest;
import com.leadrat.crm.leads.api.meeting.dto.LeadMeetingDto;
import com.leadrat.crm.leads.api.meeting.dto.UpdateLeadMeetingRequest;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import com.leadrat.crm.leads.api.user.User;
import com.leadrat.crm.leads.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CrmMeetingService {

    private static final List<Integer> REMINDER_OFFSETS = List.of(60, 10, 2);

    private final LeadRepository leadRepository;
    private final LeadMeetingRepository leadMeetingRepository;
    private final MeetingReminderRepository meetingReminderRepository;
    private final UserRepository userRepository;
    private final TenantAware tenantAware;
    private final MeetingService aiSdkMeetingService;
    private final MeetingBriefingComposer briefingComposer;

    @Transactional
    public LeadMeetingDto schedule(UUID leadId, CreateLeadMeetingRequest request) {
        UUID tenantId = requireTenant();
        Lead lead = requireLead(leadId);

        if (request.scheduledAt() == null || !request.scheduledAt().isAfter(LocalDateTime.now())) {
            throw new LeadratException("Meeting time must be in the future", HttpStatus.BAD_REQUEST);
        }

        UUID assigneeId = request.assignedUserId() != null ? request.assignedUserId() : lead.getAssignedTo();
        if (assigneeId == null) {
            assigneeId = tenantAware.getLoggedInUserId();
        }
        User assignee = userRepository.findByIdAndTenant(assigneeId, tenantId)
                .orElseThrow(() -> new LeadratException("Assignee has no CRM account yet", HttpStatus.BAD_REQUEST));
        if (assignee.getEmail() == null || assignee.getEmail().isBlank()) {
            throw new LeadratException("Assignee has no email on file", HttpStatus.BAD_REQUEST);
        }

        String title = request.title() != null && !request.title().isBlank()
                ? request.title() : briefingComposer.title(lead);
        String agenda = request.agenda() != null && !request.agenda().isBlank()
                ? request.agenda() : briefingComposer.agenda(lead);
        String timezone = request.timezone() != null && !request.timezone().isBlank()
                ? request.timezone() : "Asia/Kolkata";
        int durationMinutes = request.durationMinutes() != null && request.durationMinutes() > 0
                ? request.durationMinutes() : 60;

        LeadMeeting meeting = new LeadMeeting();
        meeting.tenant(tenantId);
        meeting.setLeadId(leadId);
        meeting.setTitle(title);
        meeting.setAgenda(agenda);
        meeting.setScheduledAt(request.scheduledAt());
        meeting.setDurationMinutes(durationMinutes);
        meeting.setTimezone(timezone);
        meeting.setAssignedUserId(assignee.getId());
        meeting.setAssignedUserEmail(assignee.getEmail());
        meeting.setAssignedUserName(assignee.getDisplayName());
        meeting.setCreatedByUserId(tenantAware.getLoggedInUserId());
        meeting.setStatus(MeetingLifecycleStatus.SCHEDULED);
        meeting.setCalendarSyncStatus("PENDING");
        leadMeetingRepository.save(meeting);

        Meeting sdkMeeting = aiSdkMeetingService.schedule(new CreateMeetingRequest(
                "Lead", leadId.toString(), title, agenda,
                request.scheduledAt().atZone(ZoneId.of(timezone)).toInstant(), durationMinutes, timezone,
                List.of(new Attendee(assignee.getEmail(), assignee.getDisplayName(), false)),
                REMINDER_OFFSETS, meeting.getId().toString()));

        meeting.setSdkMeetingId(sdkMeeting.getId());
        meeting.setMeetingLink(sdkMeeting.getMeetingLink());
        meeting.setCalendarSyncStatus(sdkMeeting.getCalendarSyncStatus());
        meeting.setCalendarSyncError(sdkMeeting.getCalendarSyncError());
        meeting.setRecallBotStatus(sdkMeeting.getRecallBotStatus());
        leadMeetingRepository.save(meeting);

        scheduleReminders(meeting);
        return LeadMeetingDto.from(meeting);
    }

    @Transactional(readOnly = true)
    public List<LeadMeetingDto> byLead(UUID leadId) {
        UUID tenantId = requireTenant();
        requireLead(leadId);
        return leadMeetingRepository.findByTenantAndLeadIdOrderByScheduledAtDesc(tenantId, leadId).stream()
                .map(LeadMeetingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LeadMeetingDto> upcoming(boolean mineOnly) {
        UUID tenantId = requireTenant();
        LocalDateTime from = LocalDateTime.now().minusHours(1);
        List<LeadMeeting> meetings = mineOnly
                ? leadMeetingRepository.findUpcomingForUser(tenantId, tenantAware.getLoggedInUserId(), from)
                : leadMeetingRepository.findUpcomingForTenant(tenantId, from);
        return meetings.stream().map(LeadMeetingDto::from).toList();
    }

    @Transactional
    public LeadMeetingDto reschedule(UUID meetingId, UpdateLeadMeetingRequest request) {
        LeadMeeting meeting = requireMeeting(meetingId);
        if (!meeting.isOpen()) {
            throw new LeadratException("Only open meetings can be edited", HttpStatus.BAD_REQUEST);
        }

        if (request.title() != null) {
            meeting.setTitle(request.title());
        }
        if (request.agenda() != null) {
            meeting.setAgenda(request.agenda());
        }
        if (request.timezone() != null) {
            meeting.setTimezone(request.timezone());
        }
        if (request.durationMinutes() != null && request.durationMinutes() > 0) {
            meeting.setDurationMinutes(request.durationMinutes());
        }
        boolean rescheduling = request.scheduledAt() != null
                && !request.scheduledAt().equals(meeting.getScheduledAt());
        if (request.scheduledAt() != null) {
            meeting.setScheduledAt(request.scheduledAt());
        }
        if (request.assignedUserId() != null && !request.assignedUserId().equals(meeting.getAssignedUserId())) {
            User assignee = userRepository.findByIdAndTenant(request.assignedUserId(), meeting.getTenant())
                    .orElseThrow(() -> new LeadratException("Assignee has no CRM account yet",
                            HttpStatus.BAD_REQUEST));
            meeting.setAssignedUserId(assignee.getId());
            meeting.setAssignedUserEmail(assignee.getEmail());
            meeting.setAssignedUserName(assignee.getDisplayName());
        }

        Meeting sdkMeeting = aiSdkMeetingService.update(meeting.getSdkMeetingId(), new UpdateMeetingRequest(
                meeting.getTitle(), meeting.getAgenda(),
                meeting.getScheduledAt().atZone(ZoneId.of(meeting.getTimezone())).toInstant(),
                meeting.getDurationMinutes(), meeting.getTimezone(),
                List.of(new Attendee(meeting.getAssignedUserEmail(), meeting.getAssignedUserName(), false)),
                REMINDER_OFFSETS));

        meeting.setMeetingLink(sdkMeeting.getMeetingLink());
        meeting.setCalendarSyncStatus(sdkMeeting.getCalendarSyncStatus());
        meeting.setCalendarSyncError(sdkMeeting.getCalendarSyncError());
        leadMeetingRepository.save(meeting);

        if (rescheduling) {
            meetingReminderRepository.deleteAll(meetingReminderRepository.findByLeadMeetingId(meeting.getId())
                    .stream().filter(reminder -> MeetingReminderStatus.PENDING.equals(reminder.getStatus())).toList());
            scheduleReminders(meeting);
        }
        return LeadMeetingDto.from(meeting);
    }

    @Transactional
    public LeadMeetingDto cancel(UUID meetingId) {
        LeadMeeting meeting = requireMeeting(meetingId);
        if (!meeting.isOpen()) {
            throw new LeadratException("Only open meetings can be cancelled", HttpStatus.BAD_REQUEST);
        }
        meeting.setStatus(MeetingLifecycleStatus.CANCELLED);
        leadMeetingRepository.save(meeting);
        aiSdkMeetingService.cancel(meeting.getSdkMeetingId());
        meetingReminderRepository.findByLeadMeetingId(meeting.getId()).forEach(reminder -> {
            if (MeetingReminderStatus.PENDING.equals(reminder.getStatus())) {
                reminder.setStatus(MeetingReminderStatus.SKIPPED);
                meetingReminderRepository.save(reminder);
            }
        });
        return LeadMeetingDto.from(meeting);
    }

    @Transactional
    public LeadMeetingDto complete(UUID meetingId) {
        LeadMeeting meeting = requireMeeting(meetingId);
        meeting.setStatus(MeetingLifecycleStatus.COMPLETED);
        leadMeetingRepository.save(meeting);
        aiSdkMeetingService.complete(meeting.getSdkMeetingId());
        return LeadMeetingDto.from(meeting);
    }

    private void scheduleReminders(LeadMeeting meeting) {
        for (int offset : REMINDER_OFFSETS) {
            LocalDateTime fireAt = meeting.getScheduledAt().minusMinutes(offset);
            MeetingReminder reminder = new MeetingReminder();
            reminder.setTenant(meeting.getTenant());
            reminder.setLeadMeetingId(meeting.getId());
            reminder.setLeadId(meeting.getLeadId());
            reminder.setOffsetMinutes(offset);
            reminder.setFireAt(fireAt);
            reminder.setStatus(fireAt.isAfter(LocalDateTime.now())
                    ? MeetingReminderStatus.PENDING : MeetingReminderStatus.SKIPPED);
            meetingReminderRepository.save(reminder);
        }
    }

    private UUID requireTenant() {
        UUID tenantId = tenantAware.getTenantId();
        if (tenantId == null) {
            throw new LeadratException("Tenant context is required for this operation", HttpStatus.PRECONDITION_FAILED);
        }
        return tenantId;
    }

    private Lead requireLead(UUID leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new LeadratException("Lead not found: " + leadId, HttpStatus.NOT_FOUND));
        tenantAware.validate(lead);
        return lead;
    }

    private LeadMeeting requireMeeting(UUID meetingId) {
        LeadMeeting meeting = leadMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new LeadratException("Meeting not found: " + meetingId, HttpStatus.NOT_FOUND));
        tenantAware.validate(meeting);
        return meeting;
    }
}
