package com.leadrat.crm.leads.api.user;

import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.tenant.TenantAwareAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * A user of this tenant's CRM.
 *
 * <p><strong>{@code User.id} IS the Cognito {@code sub}</strong> - not a separate generated id
 * plus a {@code cognitoSub} column. This is load-bearing (see the porting spec's Decision 2):
 * {@code Lead.assignedTo} and {@code LeadScope.MINE} already compare against
 * {@code TenantAware.getLoggedInUserId()}, which returns the JWT {@code sub} - so this identity
 * choice needs no migration, no join, and "is this me?" guards are a plain id comparison.
 *
 * <p>Table name is quoted ({@code "user"}) - unquoted, it collides with the Postgres reserved
 * word.
 */
@Data
@Entity
@Table(name = "\"user\"",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_tenant_email", columnNames = {"tenant", "email"}))
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class User extends TenantAwareAggregateRoot<User> {

    @Column(nullable = false)
    private String email;

    private String firstName;

    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /** An optional tenant-defined {@link com.leadrat.crm.leads.api.rbac.Role}, capped at check
     *  time by this user's own {@code role}'s default ceiling - see {@code PermissionService}. */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID customRoleId;

    /**
     * Whether this account can currently sign in and act, independent of {@link #isActive()}
     * (soft delete). Toggling this off is a reversible suspension; soft delete is not meant to
     * be reversed through the API (see {@code UserServiceImpl.delete}).
     */
    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private boolean enabled = true;

    public User() {
    }

    public User(UUID cognitoSub) {
        super(cognitoSub);
    }

    public String getDisplayName() {
        String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return name.isEmpty() ? email : name;
    }
}
