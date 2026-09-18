package com.leadrat.crm.leads.api.meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MeetingReminderRepository extends JpaRepository<MeetingReminder, UUID> {

    List<MeetingReminder> findByLeadMeetingId(UUID leadMeetingId);

    @Query("SELECT r FROM MeetingReminder r WHERE r.status = 'PENDING' AND r.fireAt <= :now "
            + "AND r.fireAt >= :missedCutoff ORDER BY r.fireAt ASC")
    List<MeetingReminder> findDue(@Param("now") LocalDateTime now, @Param("missedCutoff") LocalDateTime missedCutoff);

    @Query("SELECT r FROM MeetingReminder r WHERE r.status = 'PENDING' AND r.fireAt < :missedCutoff")
    List<MeetingReminder> findOverdue(@Param("missedCutoff") LocalDateTime missedCutoff);
}
