package com.leadrat.crm.leads.api.source.sourcecategory;

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

/** Top level of the lead source taxonomy, such as Digital or Referral. */
@Data
@Entity
@Table(name = "lead_source_category")
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CustomSourceCategory extends TenantAwareAggregateRoot<CustomSourceCategory> {

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
