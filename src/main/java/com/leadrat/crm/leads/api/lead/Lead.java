package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.core.Address;
import com.leadrat.crm.leads.api.tenant.TenantAwareAggregateRoot;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
@Table(name = "leads",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leads_tenant_project_mobile",
                columnNames = {"tenant", "project_id", "mobile_normalized"}))
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Lead extends TenantAwareAggregateRoot<Lead> {

    // ─── Identity ─────────────────────────────────────────────────────────────

    /** Unique per-tenant sequential code, e.g. LD-000042. Set once on creation. */
    @Column(name = "lead_code", length = 12)
    private String leadCode;

    private String firstName;

    private String lastName;

    @Column(nullable = false)
    private String mobile;

    /**
     * E.164 form of {@link #mobile}, and the identity half of the
     * {@code (tenant, project_id, mobile_normalized)} uniqueness key.
     *
     * <p>Derived, never entered. {@link #mobile} is what the user typed and is never rewritten;
     * this is what duplicate detection compares. The service layer sets it, and
     * {@link #deriveMobileNormalized()} guarantees it.
     */
    @Column(name = "mobile_normalized", length = 20)
    private String mobileNormalized;

    private String countryCode;

    private String alternateMobile;

    private String alternateCountryCode;

    private String email;

    private String occupation;

    @Embedded
    private Address address;

    @Enumerated(EnumType.STRING)
    private PropertyCategory propertyCategory;

    @Enumerated(EnumType.STRING)
    private PurchaseTimeline purchaseTimeline;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "temperature_id")
    private UUID temperatureId;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "status_id")
    private UUID statusId;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "source_category_id")
    private UUID sourceCategoryId;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "source_type_id")
    private UUID sourceTypeId;

    @JdbcTypeCode(Types.VARCHAR)
    private UUID assignedTo;

    @Enumerated(EnumType.STRING)
    private AssignmentMethod assignmentMethod;

    private LocalDateTime scheduleDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_nri")
    private boolean isNri = false;

    @Column(name = "is_draft")
    private boolean isDraft = false;

    // ─── Associations ─────────────────────────────────────────────────────────

    /**
     * Project this lead is enquiring about.
     *
     * <p>An opaque UUID rather than a JPA association, matching the reference, where Project lives
     * in another service. Keeping the shape means the filter and search layers port unchanged.
     */
    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "project_id")
    private UUID projectId;

    /** Channel partner who referred this lead. Opaque FK, no JPA join. */
    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "channel_partner_id")
    private UUID channelPartnerId;

    /** Telecaller or inside-sales agent assigned to this lead. */
    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "telecaller_id")
    private UUID telecallerId;

    // ─── Denormalised display names ───────────────────────────────────────────

    /**
     * Display names stored at write time.
     *
     * <p>Denormalised on purpose: the list screen shows them on every row, and the search scopes
     * ASSIGNED_TO / CHANNEL_PARTNER / TELECALLER match on them directly rather than joining.
     */
    @Column(name = "assigned_to_user_name")
    private String assignedToUserName;

    @Column(name = "channel_partner_name")
    private String channelPartnerName;

    @Column(name = "telecaller_name")
    private String telecallerName;

    // ─── Audit ────────────────────────────────────────────────────────────────

    @JdbcTypeCode(Types.VARCHAR)
    @Column(nullable = false)
    private UUID createdByUserId;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(nullable = false)
    private UUID lastModifiedByUserId;

    private LocalDateTime deletedOn;

    @JdbcTypeCode(Types.VARCHAR)
    private UUID deletedByUserId;

    // Many-to-many via the lead_tag join table. Tag ids are UUIDs stored as varchar with no FK
    // constraint, so deleting a tag cannot fail on a lead that still references it.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lead_tag", joinColumns = @JoinColumn(name = "lead_id"))
    @Column(name = "tag_id", length = 36)
    private Set<String> tagIds = new HashSet<>();

    /**
     * Keeps {@link #mobileNormalized} in step with {@link #mobile}.
     *
     * <p>The service layer sets it; this guarantees it. {@code @PreUpdate} is needed as well as
     * {@code @PrePersist} because mobile is editable. It recomputes unconditionally rather than
     * only when null: normalisation is a pure function of (mobile, countryCode), so on a save that
     * touched neither it yields the identical value and the write is a no-op.
     */
    @PrePersist
    @PreUpdate
    void deriveMobileNormalized() {
        if (mobile != null) {
            this.mobileNormalized = LeadIdentity.normalize(mobile, countryCode);
        }
    }
}
