package com.leadrat.crm.leads.api.source.sourcetype;

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
 * Second level of the lead source taxonomy, such as Facebook under Digital.
 *
 * <p>parentId points at a CustomSourceCategory as a plain UUID rather than a JPA association,
 * matching how the reference keeps cross-entity references opaque.
 */
@Data
@Entity
@Table(name = "lead_source_type")
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CustomSourceType extends TenantAwareAggregateRoot<CustomSourceType> {

    @Column(nullable = false)
    private String name;

    private String displayName;
    private String colorCode;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "parent_id")
    private UUID parentId;

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
