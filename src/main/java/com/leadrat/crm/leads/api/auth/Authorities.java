package com.leadrat.crm.leads.api.auth;

import com.leadrat.crm.leads.api.core.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Objects;
import java.util.Optional;

/**
 * Authority-to-{@link UserRole} resolution, shared between {@code TenantAware} and the RBAC
 * annotations/expressions in this package.
 *
 * <p>Cognito groups arrive bare (not {@code ROLE_}-prefixed); mirrors the private
 * {@code matches()} in {@code tenant/TenantAware.java} so both places agree on what a role
 * authority looks like.
 */
public final class Authorities {

    private Authorities() {
    }

    /** True if this authentication carries the given role, bare or {@code ROLE_}-prefixed. */
    public static boolean has(Authentication authentication, UserRole role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .anyMatch(authority -> matches(authority, role));
    }

    /** The single {@link UserRole} this authentication's JWT group maps to, if any. */
    public static Optional<UserRole> resolve(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Optional.empty();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(Authorities::toRole)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static UserRole toRole(String authority) {
        String bare = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
        for (UserRole role : UserRole.values()) {
            if (role.name().equals(bare)) {
                return role;
            }
        }
        return null;
    }

    private static boolean matches(String authority, UserRole role) {
        return authority.equals(role.name()) || authority.equals("ROLE_" + role.name());
    }
}
