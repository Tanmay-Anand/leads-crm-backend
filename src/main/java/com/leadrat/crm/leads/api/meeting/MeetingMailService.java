package com.leadrat.crm.leads.api.meeting;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class MeetingMailService {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a");

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MeetingMailService(JavaMailSender mailSender, @Value("${app.mail.from:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendReminder(LeadMeeting meeting, int offsetMinutes, String prepSummary) {
        String when = offsetMinutes >= 60 ? "1 hour" : offsetMinutes + " minute" + (offsetMinutes == 1 ? "" : "s");
        String subject = "In " + when + " — " + meeting.getTitle();
        String body = renderBody(meeting, when, prepSummary);
        send(meeting.getAssignedUserEmail(), subject, body);
    }

    public void sendScheduled(LeadMeeting meeting) {
        String subject = "Meeting scheduled — " + meeting.getTitle();
        String body = "<p>A meeting has been scheduled with you as the assignee.</p>" + meetingDetailsHtml(meeting);
        send(meeting.getAssignedUserEmail(), subject, body);
    }

    private String renderBody(LeadMeeting meeting, String when, String prepSummary) {
        StringBuilder html = new StringBuilder();
        html.append("<p>Your meeting starts in <strong>").append(when).append("</strong>.</p>");
        if (prepSummary != null && !prepSummary.isBlank()) {
            html.append("<h3>Prep</h3><p style=\"white-space:pre-wrap\">")
                    .append(escape(prepSummary)).append("</p>");
        }
        html.append(meetingDetailsHtml(meeting));
        return html.toString();
    }

    private String meetingDetailsHtml(LeadMeeting meeting) {
        StringBuilder html = new StringBuilder();
        html.append("<p><strong>When:</strong> ").append(meeting.getScheduledAt().format(DISPLAY_FORMAT))
                .append(" (").append(meeting.getTimezone()).append(")</p>");
        if (meeting.getAgenda() != null && !meeting.getAgenda().isBlank()) {
            html.append("<p><strong>Agenda:</strong> ").append(escape(meeting.getAgenda())).append("</p>");
        }
        if (meeting.getMeetingLink() != null) {
            html.append("<p><a href=\"").append(meeting.getMeetingLink()).append("\">Join the meeting</a></p>");
        }
        return html.toString();
    }

    private void send(String to, String subject, String htmlBody) {
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("meeting mail: failed to send '{}' to {} ({})", subject, to, e.getMessage());
        }
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
    }
}
