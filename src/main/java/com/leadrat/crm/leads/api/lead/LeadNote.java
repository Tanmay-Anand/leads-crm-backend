package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.tenant.TenantAwareAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.util.UUID;

/** A user-recorded activity note against a lead. */
@Data
@Entity
@Table(name = "lead_note")
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class LeadNote extends TenantAwareAggregateRoot<LeadNote> {

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadNoteType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    /** Optional URL or reference, such as a document link or call recording. */
    private String externalReference;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "performed_by_user_id")
    private UUID performedByUserId;

    @Column(name = "performed_by_username")
    private String performedByUsername;
}
