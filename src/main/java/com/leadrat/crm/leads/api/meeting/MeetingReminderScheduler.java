package com.leadrat.crm.leads.api.meeting;

import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.lead.Lead;
import com.leadrat.crm.leads.api.lead.LeadRepository;
import com.leadrat.crm.leads.api.tenant.TenantContext;
import com.leadrat.crm.leads.api.util.SystemToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingReminderScheduler {

    private static final int MISSED_CUTOFF_MINUTES = 10;

    private final MeetingReminderRepository meetingReminderRepository;
    private final LeadMeetingRepository leadMeetingRepository;
    private final LeadRepository leadRepository;
    private final MeetingBriefingComposer briefingComposer;
    private final MeetingMailService mailService;

    @Scheduled(fixedDelay = 30_000)
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime missedCutoff = now.minusMinutes(MISSED_CUTOFF_MINUTES);

        List<MeetingReminder> overdue = meetingReminderRepository.findOverdue(missedCutoff);
        for (MeetingReminder reminder : overdue) {
            reminder.setStatus(MeetingReminderStatus.MISSED);
            meetingReminderRepository.save(reminder);
        }

        List<MeetingReminder> due = meetingReminderRepository.findDue(now, missedCutoff);
        for (MeetingReminder reminder : due) {
            fire(reminder);
        }
    }

    @Transactional
    void fire(MeetingReminder reminder) {
        try {
            TenantContext.setCurrentTenant(reminder.getTenant());
            SecurityContextHolder.getContext()
                    .setAuthentication(new SystemToken(reminder.getTenant(), UserRole.TENANT_ADMIN));
            Optional<LeadMeeting> meetingOpt = leadMeetingRepository.findById(reminder.getLeadMeetingId());
            if (meetingOpt.isEmpty() || !meetingOpt.get().isOpen()) {
                reminder.setStatus(MeetingReminderStatus.SKIPPED);
                meetingReminderRepository.save(reminder);
                return;
            }
            LeadMeeting meeting = meetingOpt.get();
            Optional<Lead> leadOpt = leadRepository.findById(reminder.getLeadId());
            String prepSummary = leadOpt.map(briefingComposer::prepSummary).orElse(null);
            mailService.sendReminder(meeting, reminder.getOffsetMinutes(), prepSummary);
            reminder.setStatus(MeetingReminderStatus.SENT);
            reminder.setSentAt(LocalDateTime.now());
            meetingReminderRepository.save(reminder);
        } catch (Exception e) {
            log.warn("meeting reminder: failed to fire {} ({})", reminder.getId(), e.getMessage());
            reminder.setStatus(MeetingReminderStatus.FAILED);
            meetingReminderRepository.save(reminder);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
