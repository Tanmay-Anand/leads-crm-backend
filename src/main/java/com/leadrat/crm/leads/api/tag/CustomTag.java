package com.leadrat.crm.leads.api.tag;

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

/** Tenant-configurable free-form label attachable to many leads. */
@Data
@Entity
@Table(name = "tag")
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CustomTag extends TenantAwareAggregateRoot<CustomTag> {

    @Column(nullable = false)
    private String name;

    private String displayName;
    private String colorCode;

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
