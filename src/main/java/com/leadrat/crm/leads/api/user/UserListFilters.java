package com.leadrat.crm.leads.api.user;

import com.leadrat.crm.leads.api.core.UserRole;

import java.util.UUID;

/** Optional equality filters for {@code GET /users} - the small, purpose-built panel the frontend
 *  offers instead of a full advanced-filter drawer (v1 scope decision, see the porting spec). */
public record UserListFilters(Boolean enabled, UserRole role, UUID customRoleId) {

    public static final UserListFilters NONE = new UserListFilters(null, null, null);

    public boolean isEmpty() {
        return enabled == null && role == null && customRoleId == null;
    }
}
