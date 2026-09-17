package com.leadrat.crm.leads.api.leadstatus;

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

/**
 * A pipeline stage a lead can sit on. Tenant-configurable, which is why this is a table rather
 * than an enum.
 */
@Data
@Entity
@Table(name = "lead_status")
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class LeadStatus extends TenantAwareAggregateRoot<LeadStatus> {

    @Column(nullable = false)
    private String name;

    private String displayName;
    private String colorCode;

    /** Marks the status that new leads are assigned to. At most one per tenant. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    /** When true, a note must be provided before a lead can transition to this status. */
    @Column(name = "is_note_required", nullable = false)
    private boolean isNoteRequired = false;

    @Column(nullable = false)
    private int displayOrder = 0;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(nullable = false)
    private UUID createdByUserId;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(nullable = false)
    private UUID lastModifiedByUserId;

    private LocalDateTime deletedOn;

    @JdbcTypeCode(Types.VARCHAR)
    private UUID deletedByUserId;
}
