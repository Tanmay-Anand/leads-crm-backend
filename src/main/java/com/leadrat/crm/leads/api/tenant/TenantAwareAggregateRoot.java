package com.leadrat.crm.leads.api.tenant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.leadrat.crm.leads.api.core.AggregateRoot;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Base class for every tenant-scoped aggregate root.
 *
 * <p>Two things happen here that callers never have to remember: {@code @SQLRestriction} hides
 * soft-deleted rows from every read, and the {@code tenantFilter} definition is what
 * {@link TenantFilterAspect} enables per request so a query cannot cross tenants.
 *
 * @param <A> the concrete aggregate root type, for fluent API support
 * @see TenantAwareEntityListener
 * @see TenantFilterAspect
 */
@Getter
@ToString
@MappedSuperclass
@SuppressWarnings("unchecked")
@SQLRestriction("is_active = true")
@EqualsAndHashCode(callSuper = true)
@EntityListeners(TenantAwareEntityListener.class)
@Filter(name = "tenantFilter", condition = "(tenant = :tenantId or tenant = '" + TenantAwareAggregateRoot.SYSTEM_TENANT_ID_STRING + "')")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
public class TenantAwareAggregateRoot<A extends TenantAwareAggregateRoot<A>> extends AggregateRoot<A> {

    public static final String SYSTEM_TENANT_ID_STRING = "00000000-0000-0000-0000-000000000000";
    public static final UUID SYSTEM_TENANT_ID = UUID.fromString(SYSTEM_TENANT_ID_STRING);

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID tenant;

    /**
     * Sets the tenant ID for this entity.
     *
     * @param tenant the tenant UUID
     * @return this entity for fluent chaining
     */
    public A tenant(UUID tenant) {
        this.tenant = tenant;
        return (A) this;
    }

    /** Soft delete — flips is_active, which every read already filters on. */
    public void softDelete() {
        terminate();
    }
}
