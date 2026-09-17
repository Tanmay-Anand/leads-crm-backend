package com.leadrat.crm.leads.api.channelpartner;

import com.leadrat.crm.leads.api.core.Address;
import com.leadrat.crm.leads.api.tenant.TenantAwareAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A channel partner firm or individual broker enrolled with this tenant.
 *
 * <p>Modelled on platform-api {@code ChannelPartnerEnrollment}, the tenant-scoped half of the pair
 * there. The global {@code ChannelPartner} identity, which exists so one real firm can enrol with
 * many builders and sign in to a partner portal, is deliberately not reproduced: there is no
 * partner portal here, so a single tenant-scoped record says everything this CRM needs and avoids
 * a two-table join on every read. See README.md for the full list of deviations.
 */
@Data
@Entity
@Table(name = "channel_partner",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_channel_partner_tenant_email",
                columnNames = {"tenant", "email"}))
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ChannelPartner extends TenantAwareAggregateRoot<ChannelPartner> {

    // ─── Step 1: firm info ────────────────────────────────────────────────────

    /** Firm or agency name. */
    @Column(length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", length = 30, nullable = false)
    private ChannelPartnerType partnerType = ChannelPartnerType.CHANNEL_PARTNER;

    /** Owner or point-of-contact full name. */
    @Column(name = "owner_poc_name", length = 200)
    private String ownerPocName;

    @Column(name = "primary_country_code", length = 10)
    private String primaryCountryCode;

    @Column(name = "primary_phone", length = 20)
    private String primaryPhone;

    @Column(name = "secondary_country_code", length = 10)
    private String secondaryCountryCode;

    @Column(name = "secondary_phone", length = 20)
    private String secondaryPhone;

    @Column(length = 255, nullable = false)
    private String email;

    @Column(name = "alternate_email", length = 255)
    private String alternateEmail;

    @Column(name = "rera_reg_number", length = 50)
    private String reraRegNumber;

    @Column(name = "rera_verified")
    private boolean reraVerified = false;

    // ─── Step 2: contact and team ─────────────────────────────────────────────

    @Embedded
    private Address address;

    @Column(name = "whatsapp_no", length = 20)
    private String whatsappNo;

    @Column(name = "number_of_sales_agents")
    private Integer numberOfSalesAgents;

    @Column(name = "years_in_business")
    private Integer yearsInBusiness;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_market", length = 40)
    private PrimaryMarket primaryMarket;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    // ─── Step 3: KYC and banking ──────────────────────────────────────────────

    @Column(name = "owner_pan", length = 10)
    private String ownerPan;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_number", length = 30)
    private String bankAccountNumber;

    @Column(name = "ifsc_code", length = 15)
    private String ifscCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20)
    private BankAccountType accountType;

    // ─── Step 4: commission and tier ──────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ChannelPartnerTier tier;

    /**
     * Projects this partner is authorised to sell, as a JSON array of project ids.
     *
     * <p>JSON rather than a join table, as in the reference: the list is read whole with the
     * partner and never queried across partners.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assigned_projects")
    private List<UUID> assignedProjects;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", length = 30)
    private CommissionType commissionType;

    @Column(name = "commission_rate", precision = 10, scale = 4)
    private BigDecimal commissionRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_payout_trigger", length = 40)
    private CommissionPayoutTrigger commissionPayoutTrigger;

    /** Days from the payout trigger to actual payment. */
    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    // ─── Status ───────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", length = 30, nullable = false)
    private ChannelPartnerOnboardingStatus onboardingStatus = ChannelPartnerOnboardingStatus.DRAFT;
}
