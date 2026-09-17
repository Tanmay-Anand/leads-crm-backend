package com.leadrat.crm.leads.api.search.advanced;

/**
 * How an advanced-search endpoint treats a criterion the registry cannot serve, whether an
 * unknown field key, a disallowed operator, or a value that will not coerce.
 *
 * <p>REJECT suits an interactive search, where a bad criterion means the caller has a bug and a
 * silent narrowing would hide it. SKIP suits replaying a saved filter, whose criteria were written
 * against an older field set and may name a field that no longer exists.
 */
public enum UnknownFieldPolicy {

    REJECT,
    SKIP;

    public boolean isReject() {
        return this == REJECT;
    }
}
