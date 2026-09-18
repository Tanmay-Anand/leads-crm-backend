package com.leadrat.crm.leads.api.meeting;

import com.leadrat.aisdk.meeting.Discussion;
import com.leadrat.aisdk.meeting.MeetingService;
import com.leadrat.crm.leads.api.lead.Lead;
import com.leadrat.crm.leads.api.lead.LeadNote;
import com.leadrat.crm.leads.api.lead.LeadNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MeetingBriefingComposer {

    private static final int TRANSCRIPT_CHAR_LIMIT = 700;

    private final LeadNoteRepository leadNoteRepository;
    private final MeetingService aiSdkMeetingService;

    public String title(Lead lead) {
        String name = leadName(lead);
        String subject = lead.getPropertyCategory() == null ? "" : " · " + titleCase(lead.getPropertyCategory().name());
        return name + subject;
    }

    public String agenda(Lead lead) {
        StringBuilder agenda = new StringBuilder();
        agenda.append("Meeting with ").append(leadName(lead)).append('.');
        if (lead.getPurchaseTimeline() != null) {
            agenda.append(" Purchase timeline: ").append(titleCase(lead.getPurchaseTimeline().name())).append('.');
        }
        if (lead.getPropertyCategory() != null) {
            agenda.append(" Property category: ").append(titleCase(lead.getPropertyCategory().name())).append('.');
        }
        if (lead.getNotes() != null && !lead.getNotes().isBlank()) {
            agenda.append(" Notes: ").append(lead.getNotes());
        }
        return agenda.toString();
    }

    public String prepSummary(Lead lead) {
        StringBuilder summary = new StringBuilder();
        summary.append(leadName(lead));
        if (lead.getPropertyCategory() != null) {
            summary.append(" · ").append(titleCase(lead.getPropertyCategory().name()));
        }
        if (lead.getPurchaseTimeline() != null) {
            summary.append(" · timeline ").append(titleCase(lead.getPurchaseTimeline().name()));
        }
        summary.append('\n');

        List<LeadNote> notes = leadNoteRepository
                .findByTenantAndLeadIdAndIsActiveTrueOrderByCreatedDesc(lead.getTenant(), lead.getId());
        if (!notes.isEmpty()) {
            LeadNote latest = notes.get(0);
            summary.append("Last note (").append(latest.getType()).append("): ").append(latest.getBody())
                    .append('\n');
        }

        List<Discussion> discussions = aiSdkMeetingService.leadDiscussions(lead.getId().toString(), 1);
        if (!discussions.isEmpty()) {
            String transcript = discussions.get(0).discussion();
            if (transcript != null && !transcript.isBlank()) {
                String truncated = transcript.length() > TRANSCRIPT_CHAR_LIMIT
                        ? transcript.substring(0, TRANSCRIPT_CHAR_LIMIT) + "…"
                        : transcript;
                summary.append("From the last call: ").append(truncated);
            }
        }
        return summary.toString().trim();
    }

    private String leadName(Lead lead) {
        String name = ((lead.getFirstName() == null ? "" : lead.getFirstName()) + " "
                + (lead.getLastName() == null ? "" : lead.getLastName())).trim();
        return name.isEmpty() ? lead.getMobile() : name;
    }

    private String titleCase(String value) {
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
