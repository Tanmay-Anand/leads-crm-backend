package com.leadrat.crm.leads.api.meeting;

import com.leadrat.crm.leads.api.auth.annotations.AuthenticatedOnly;
import com.leadrat.crm.leads.api.meeting.dto.CreateLeadMeetingRequest;
import com.leadrat.crm.leads.api.meeting.dto.LeadMeetingDto;
import com.leadrat.crm.leads.api.meeting.dto.UpdateLeadMeetingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Meeting", description = "Meeting links generated against a lead, backed by the AI SDK")
public class MeetingController {

    private final CrmMeetingService meetingService;

    @Operation(summary = "Generate a meeting link for a lead and assign it to a platform user")
    @PostMapping("/leads/{leadId}/meetings")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadMeetingDto> schedule(@PathVariable UUID leadId,
                                                   @Valid @RequestBody CreateLeadMeetingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.schedule(leadId, request));
    }

    @Operation(summary = "List every meeting generated for a lead")
    @GetMapping("/leads/{leadId}/meetings")
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<List<LeadMeetingDto>> byLead(@PathVariable UUID leadId) {
        return ResponseEntity.ok(meetingService.byLead(leadId));
    }

    @Operation(summary = "Upcoming meetings, mine or the whole tenant's")
    @GetMapping("/meetings/upcoming")
    @AuthenticatedOnly
    public ResponseEntity<List<LeadMeetingDto>> upcoming(
            @RequestParam(required = false, defaultValue = "me") String scope) {
        return ResponseEntity.ok(meetingService.upcoming(!"team".equalsIgnoreCase(scope)));
    }

    @Operation(summary = "Reschedule, retitle or reassign a meeting")
    @PatchMapping("/meetings/{meetingId}")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadMeetingDto> reschedule(@PathVariable UUID meetingId,
                                                     @Valid @RequestBody UpdateLeadMeetingRequest request) {
        return ResponseEntity.ok(meetingService.reschedule(meetingId, request));
    }

    @Operation(summary = "Cancel a meeting")
    @PostMapping("/meetings/{meetingId}/cancel")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadMeetingDto> cancel(@PathVariable UUID meetingId) {
        return ResponseEntity.ok(meetingService.cancel(meetingId));
    }

    @Operation(summary = "Mark a meeting complete")
    @PostMapping("/meetings/{meetingId}/complete")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadMeetingDto> complete(@PathVariable UUID meetingId) {
        return ResponseEntity.ok(meetingService.complete(meetingId));
    }
}
