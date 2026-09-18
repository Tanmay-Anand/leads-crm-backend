package com.leadrat.crm.leads.api.meeting;

import com.leadrat.crm.leads.api.tenant.TenantAwareAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "lead_meeting")
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class LeadMeeting extends TenantAwareAggregateRoot<LeadMeeting> {

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "sdk_meeting_id")
    private String sdkMeetingId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String agenda;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;

    private String timezone;

    @Column(name = "meeting_link")
    private String meetingLink;

    @Column(nullable = false)
    private String status = MeetingLifecycleStatus.SCHEDULED;

    @Column(name = "calendar_sync_status")
    private String calendarSyncStatus;

    @Column(name = "calendar_sync_error", columnDefinition = "TEXT")
    private String calendarSyncError;

    @Column(name = "recall_bot_status")
    private String recallBotStatus;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "assigned_user_email")
    private String assignedUserEmail;

    @Column(name = "assigned_user_name")
    private String assignedUserName;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    public boolean isOpen() {
        return MeetingLifecycleStatus.SCHEDULED.equals(status);
    }
}
