package com.leadrat.crm.leads.api.channelpartner;

import com.leadrat.crm.leads.api.core.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read and write model for a channel partner.
 *
 * <p>One record for both directions, matching the project module and the reference onboarding
 * wizard, which posts back the whole partner on every step.
 */
public record ChannelPartnerDto(
        UUID id,
        UUID tenantId,
        @NotBlank(message = "name is required") String name,
        ChannelPartnerType partnerType,
        String ownerPocName,
        String primaryCountryCode,
        String primaryPhone,
        String secondaryCountryCode,
        String secondaryPhone,
        @NotBlank(message = "email is required") @Email(message = "email must be a valid address") String email,
        String alternateEmail,
        String reraRegNumber,
        Boolean reraVerified,
        @Valid Address address,
        String whatsappNo,
        Integer numberOfSalesAgents,
        Integer yearsInBusiness,
        PrimaryMarket primaryMarket,
        String gstNumber,
        String ownerPan,
        String bankName,
        String bankAccountNumber,
        String ifscCode,
        BankAccountType accountType,
        ChannelPartnerTier tier,
        List<UUID> assignedProjects,
        CommissionType commissionType,
        BigDecimal commissionRate,
        CommissionPayoutTrigger commissionPayoutTrigger,
        Integer paymentTermsDays,
        ChannelPartnerOnboardingStatus onboardingStatus,
        /** Active leads attributed to this partner. Populated on the list and detail reads. */
        Long leadCount,
        LocalDateTime created,
        String createdBy,
        LocalDateTime modified,
        String lastModifiedBy,
        Boolean isActive
) {

    /** Applies the writable fields onto an entity, leaving identity and audit columns alone. */
    public void applyTo(ChannelPartner partner) {
        partner.setName(name);
        partner.setPartnerType(partnerType != null ? partnerType : ChannelPartnerType.CHANNEL_PARTNER);
        partner.setOwnerPocName(ownerPocName);
        partner.setPrimaryCountryCode(primaryCountryCode);
        partner.setPrimaryPhone(primaryPhone);
        partner.setSecondaryCountryCode(secondaryCountryCode);
        partner.setSecondaryPhone(secondaryPhone);
        partner.setEmail(email);
        partner.setAlternateEmail(alternateEmail);
        partner.setReraRegNumber(reraRegNumber);
        partner.setReraVerified(Boolean.TRUE.equals(reraVerified));
        partner.setAddress(address);
        partner.setWhatsappNo(whatsappNo);
        partner.setNumberOfSalesAgents(numberOfSalesAgents);
        partner.setYearsInBusiness(yearsInBusiness);
        partner.setPrimaryMarket(primaryMarket);
        partner.setGstNumber(gstNumber);
        partner.setOwnerPan(ownerPan);
        partner.setBankName(bankName);
        partner.setBankAccountNumber(bankAccountNumber);
        partner.setIfscCode(ifscCode);
        partner.setAccountType(accountType);
        partner.setTier(tier);
        partner.setAssignedProjects(assignedProjects);
        partner.setCommissionType(commissionType);
        partner.setCommissionRate(resolveCommissionRate());
        partner.setCommissionPayoutTrigger(commissionPayoutTrigger);
        partner.setPaymentTermsDays(paymentTermsDays);
        partner.setOnboardingStatus(
                onboardingStatus != null ? onboardingStatus : ChannelPartnerOnboardingStatus.DRAFT);
    }

    /**
     * Falls back to the tier default when no explicit rate was given.
     *
     * <p>Leaving it null would make a partner look like a zero-commission partner rather than one
     * on the standard rate for their tier.
     */
    private BigDecimal resolveCommissionRate() {
        if (commissionRate != null) {
            return commissionRate;
        }
        return tier != null ? tier.getDefaultCommissionRate() : null;
    }

    public ChannelPartner toEntity() {
        ChannelPartner partner = new ChannelPartner();
        applyTo(partner);
        return partner;
    }

    public static ChannelPartnerDto fromEntity(ChannelPartner p) {
        return fromEntity(p, null);
    }

    public static ChannelPartnerDto fromEntity(ChannelPartner p, Long leadCount) {
        return new ChannelPartnerDto(
                p.getId(),
                p.getTenant(),
                p.getName(),
                p.getPartnerType(),
                p.getOwnerPocName(),
                p.getPrimaryCountryCode(),
                p.getPrimaryPhone(),
                p.getSecondaryCountryCode(),
                p.getSecondaryPhone(),
                p.getEmail(),
                p.getAlternateEmail(),
                p.getReraRegNumber(),
                p.isReraVerified(),
                p.getAddress(),
                p.getWhatsappNo(),
                p.getNumberOfSalesAgents(),
                p.getYearsInBusiness(),
                p.getPrimaryMarket(),
                p.getGstNumber(),
                p.getOwnerPan(),
                p.getBankName(),
                p.getBankAccountNumber(),
                p.getIfscCode(),
                p.getAccountType(),
                p.getTier(),
                p.getAssignedProjects(),
                p.getCommissionType(),
                p.getCommissionRate(),
                p.getCommissionPayoutTrigger(),
                p.getPaymentTermsDays(),
                p.getOnboardingStatus(),
                leadCount,
                p.getCreated(),
                p.getCreatedBy(),
                p.getModified(),
                p.getLastModifiedBy(),
                p.isActive());
    }
}
