package com.leadrat.crm.leads.api.rbac;

import com.leadrat.crm.leads.api.tenant.TenantAwareAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * A tenant-defined custom role: a flat {@code action:resource} permission list a {@link
 * com.leadrat.crm.leads.api.user.User} can be assigned in addition to its Cognito-group {@code
 * UserRole}.
 *
 * <p>Ported from builder-crm's model (flat JSONB list, no junction tables), with one addition
 * that model never had: what this role actually grants is capped by {@code
 * PermissionService.deriveDefaultPermissions(jwtRole)} at check time - a custom role can only
 * ever subtract from its holder's Cognito-group ceiling, never add to it. See {@code
 * PermissionService}'s class doc.
 */
@Data
@Entity
@Table(name = "role",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_tenant_name", columnNames = {"tenant", "name"}))
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Role extends TenantAwareAggregateRoot<Role> {

    @Column(nullable = false)
    private String name;

    /** Flat list of {@code action:resource} strings from {@link CrmPermission#ALL}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> permissions = new ArrayList<>();

    /** Seeded at tenant provisioning, immutable via the API (see {@code RoleServiceImpl}). */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean isSystem = false;
}
