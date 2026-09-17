package com.leadrat.crm.leads.api.lead.dto;

import com.leadrat.crm.leads.api.core.Address;
import com.leadrat.crm.leads.api.lead.AssignmentMethod;
import com.leadrat.crm.leads.api.lead.PropertyCategory;
import com.leadrat.crm.leads.api.lead.PurchaseTimeline;
import com.leadrat.crm.leads.api.util.Validations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Write model for creating and fully replacing a lead, used by both POST and PUT.
 *
 * <p>The same required fields apply to both, because PUT replaces the whole resource.
 */
public record CreateLeadRequest(
        @NotBlank(message = "firstName is required") String firstName,
        String lastName,
        @NotBlank(message = "mobile is required") String mobile,
        String countryCode,
        String alternateMobile,
        String alternateCountryCode,
        String email,
        String occupation,
        @Valid Address address,
        @NotNull(message = "propertyCategory is required") PropertyCategory propertyCategory,
        PurchaseTimeline purchaseTimeline,
        UUID projectId,
        UUID channelPartnerId,
        UUID telecallerId,
        String telecallerName,
        UUID assignedTo,
        String assignedToUserName,
        AssignmentMethod assignmentMethod,
        UUID statusId,
        UUID temperatureId,
        UUID sourceCategoryId,
        UUID sourceTypeId,
        List<UUID> tagIds,
        String notes,
        boolean isNri,
        boolean isDraft,
        LocalDateTime scheduleDate
) {

    /**
     * Format checks that Bean Validation cannot express.
     *
     * <p>Called explicitly by the service rather than wired as a constraint, so the error carries
     * the same shape as every other LeadratException.
     */
    public void validate() {
        if (mobile != null && !mobile.isBlank()) {
            Validations.isValidMobile(mobile);
        }
        if (alternateMobile != null && !alternateMobile.isBlank()) {
            Validations.isValidMobile(alternateMobile);
        }
        if (email != null && !email.isBlank()) {
            Validations.isValidEmail(email);
        }
    }
}
