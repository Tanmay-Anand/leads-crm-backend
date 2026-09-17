package com.leadrat.crm.leads.api.lead;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.util.UUID;

/**
 * Per-tenant counter behind the sequential lead codes.
 *
 * <p>Its primary key is the tenant id, so there is exactly one row per tenant. Not a
 * TenantAwareAggregateRoot: it is a counter rather than an aggregate, and it must be readable
 * without the tenant filter in order to be locked.
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "lead_sequence")
public class LeadSequence {

    @Id
    @JdbcTypeCode(Types.VARCHAR)
    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private long lastValue = 0L;

    @Version
    private long version;

    public LeadSequence(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
