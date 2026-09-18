package com.leadrat.crm.leads.api.meeting;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadMeetingRepository extends TenantAwareRepository<LeadMeeting> {

    List<LeadMeeting> findByTenantAndLeadIdOrderByScheduledAtDesc(UUID tenant, UUID leadId);

    Optional<LeadMeeting> findBySdkMeetingId(String sdkMeetingId);

    @Query("SELECT m FROM LeadMeeting m WHERE m.tenant = :tenant AND m.scheduledAt >= :from "
            + "ORDER BY m.scheduledAt ASC")
    List<LeadMeeting> findUpcomingForTenant(@Param("tenant") UUID tenant, @Param("from") LocalDateTime from);

    @Query("SELECT m FROM LeadMeeting m WHERE m.tenant = :tenant AND m.assignedUserId = :userId "
            + "AND m.scheduledAt >= :from ORDER BY m.scheduledAt ASC")
    List<LeadMeeting> findUpcomingForUser(@Param("tenant") UUID tenant, @Param("userId") UUID userId,
                                          @Param("from") LocalDateTime from);
}
