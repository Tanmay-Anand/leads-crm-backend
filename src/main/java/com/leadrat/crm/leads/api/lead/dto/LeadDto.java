package com.leadrat.crm.leads.api.lead.dto;

import com.leadrat.crm.leads.api.core.Address;
import com.leadrat.crm.leads.api.lead.AssignmentMethod;
import com.leadrat.crm.leads.api.lead.Lead;
import com.leadrat.crm.leads.api.lead.PropertyCategory;
import com.leadrat.crm.leads.api.lead.PurchaseTimeline;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusDto;
import com.leadrat.crm.leads.api.source.sourcecategory.dto.SourceCategoryDto;
import com.leadrat.crm.leads.api.source.sourcetype.dto.SourceTypeDto;
import com.leadrat.crm.leads.api.tag.dto.TagDto;
import com.leadrat.crm.leads.api.temperature.dto.TemperatureDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read model for a lead, returned by every lead endpoint.
 *
 * <p>The id-valued columns on the entity are expanded into their objects here, so the list screen
 * can render a status pill or a temperature chip without a second request per row.
 */
public record LeadDto(
        UUID id,
        UUID tenantId,
        String leadCode,
        String firstName,
        String lastName,
        String mobile,
        String countryCode,
        String alternateMobile,
        String alternateCountryCode,
        String email,
        String occupation,
        Address address,
        PropertyCategory propertyCategory,
        PurchaseTimeline purchaseTimeline,
        TemperatureDto temperature,
        LeadStatusDto status,
        SourceCategoryDto sourceCategory,
        SourceTypeDto sourceType,
        UUID assignedTo,
        String assignedToName,
        AssignmentMethod assignmentMethod,
        LocalDateTime scheduleDate,
        /** Derived from scheduleDate: OVERDUE, DUE_TODAY or ON_TRACK. Null when nothing is scheduled. */
        String slaStatus,
        String notes,
        boolean isNri,
        boolean isDraft,
        UUID projectId,
        UUID channelPartnerId,
        String channelPartnerName,
        UUID telecallerId,
        String telecallerName,
        List<TagDto> tags,
        UUID createdByUserId,
        UUID lastModifiedByUserId,
        LocalDateTime createdOn,
        LocalDateTime modifiedOn,
        String createdBy,
        String lastModifiedBy,
        boolean isDeleted,
        LocalDateTime deletedOn,
        UUID deletedByUserId,
        Boolean isActive
) {

    public static LeadDto from(Lead lead,
                               LeadStatusDto status,
                               TemperatureDto temperature,
                               SourceCategoryDto sourceCategory,
                               SourceTypeDto sourceType,
                               List<TagDto> tags) {
        return new LeadDto(
                lead.getId(),
                lead.getTenant(),
                lead.getLeadCode(),
                lead.getFirstName(),
                lead.getLastName(),
                lead.getMobile(),
                lead.getCountryCode(),
                lead.getAlternateMobile(),
                lead.getAlternateCountryCode(),
                lead.getEmail(),
                lead.getOccupation(),
                lead.getAddress(),
                lead.getPropertyCategory(),
                lead.getPurchaseTimeline(),
                temperature,
                status,
                sourceCategory,
                sourceType,
                lead.getAssignedTo(),
                lead.getAssignedToUserName(),
                lead.getAssignmentMethod(),
                lead.getScheduleDate(),
                deriveSlaStatus(lead),
                lead.getNotes(),
                lead.isNri(),
                lead.isDraft(),
                lead.getProjectId(),
                lead.getChannelPartnerId(),
                lead.getChannelPartnerName(),
                lead.getTelecallerId(),
                lead.getTelecallerName(),
                tags,
                lead.getCreatedByUserId(),
                lead.getLastModifiedByUserId(),
                lead.getCreated(),
                lead.getModified(),
                lead.getCreatedBy(),
                lead.getLastModifiedBy(),
                !lead.isActive(),
                lead.getDeletedOn(),
                lead.getDeletedByUserId(),
                lead.isActive());
    }

    /**
     * Computed rather than stored, because it changes with the calendar rather than with the lead:
     * a stored value would go stale at midnight with nothing writing to the row.
     */
    private static String deriveSlaStatus(Lead lead) {
        if (lead.getScheduleDate() == null) {
            return null;
        }
        LocalDate schedule = lead.getScheduleDate().toLocalDate();
        LocalDate today = LocalDate.now();
        if (schedule.isBefore(today)) {
            return "OVERDUE";
        }
        if (schedule.isEqual(today)) {
            return "DUE_TODAY";
        }
        return "ON_TRACK";
    }
}
