package com.leadrat.crm.leads.api.user.dto;

import com.leadrat.crm.leads.api.core.UserRole;

import java.util.List;
import java.util.UUID;

/**
 * GET /users/me. Wire contract the frontend's authorization core relies on: {@code permissions}
 * is always a real array (never null), and {@code unrestricted} is an explicit boolean rather
 * than a {@code null = full access} sentinel - the frontend keeps that sentinel off the wire on
 * purpose, since conflating "unrestricted" with "not loaded yet" is exactly the bug this port
 * fixes relative to the reference.
 */
public record MeResponse(
        UUID id,
        UUID tenantId,
        String email,
        String displayName,
        UserRole role,
        /** True only for PLATFORM_ADMIN/TENANT_ADMIN (see PermissionService's admin short-circuit) -
         *  the frontend grants everything without consulting {@code permissions} at all. */
        boolean unrestricted,
        List<String> permissions
) {
}
