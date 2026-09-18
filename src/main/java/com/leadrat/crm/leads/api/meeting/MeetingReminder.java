package com.leadrat.crm.leads.api.meeting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "meeting_reminder",
        uniqueConstraints = @UniqueConstraint(name = "uk_reminder_meeting_offset",
                columnNames = {"lead_meeting_id", "offset_minutes"}))
@NoArgsConstructor
public class MeetingReminder {

    @Id
    @JdbcTypeCode(Types.VARCHAR)
    private UUID id = UUID.randomUUID();

    @JdbcTypeCode(Types.VARCHAR)
    @Column(nullable = false)
    private UUID tenant;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "lead_meeting_id", nullable = false)
    private UUID leadMeetingId;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "offset_minutes", nullable = false)
    private int offsetMinutes;

    @Column(name = "fire_at", nullable = false)
    private LocalDateTime fireAt;

    @Column(nullable = false)
    private String status = MeetingReminderStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
