package com.leadrat.crm.leads.api.util;

import com.leadrat.crm.leads.api.core.UserRole;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.List;
import java.util.UUID;

/**
 * Authentication used by internal, non-request-bound work such as tenant seeding, where there
 * is no JWT to derive a tenant from. {@code TenantAware} and {@code TenantFilterAspect} both
 * recognise it and take the tenant straight off the token.
 */
public class SystemToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String principal;

    @Getter
    private final UUID tenantId;

    public SystemToken(UUID tenantId, UserRole role) {
        super(List.of(new SimpleGrantedAuthority(role.name())));
        this.principal = "System :: " + tenantId;
        this.tenantId = tenantId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }
}
