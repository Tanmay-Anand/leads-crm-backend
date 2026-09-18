package com.leadrat.crm.leads.api.rbac;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The permission catalogue: every valid {@code action:resource} string this backend actually
 * enforces.
 *
 * <p>Trimmed from builder-crm's model (a single flat map, not a hierarchy) to the modules
 * leads-crm actually has. No {@code export}/{@code add_bulk}/{@code approve}/{@code generate} -
 * there are no endpoints for them, and a permission with no enforcement point is a checkbox that
 * grants nothing (IMPLEMENTATION_PLAN.md-style honesty: don't offer what isn't real).
 *
 * <p>Lead notes fold into {@code leads} (they have no separate resource). Source categories and
 * types share {@code master-data/sources} - the UI treats them as one taxonomy.
 */
public final class CrmPermission {

    public static final Map<String, List<String>> CATALOG;
    public static final Set<String> ALL;

    static {
        Map<String, List<String>> catalog = new LinkedHashMap<>();

        catalog.put("leads", List.of("view", "add", "update", "delete", "assign"));
        catalog.put("projects", List.of("view", "add", "update", "delete"));
        catalog.put("channel-partners", List.of("view", "add", "update", "delete"));
        catalog.put("users", List.of("view", "add", "update", "delete", "toggle"));
        catalog.put("roles", List.of("view", "add", "update", "delete"));
        catalog.put("master-data/lead-statuses", List.of("view", "add", "update", "delete"));
        catalog.put("master-data/tags", List.of("view", "add", "update", "delete"));
        catalog.put("master-data/temperatures", List.of("view", "add", "update", "delete"));
        catalog.put("master-data/sources", List.of("view", "add", "update", "delete"));

        // The AI pre-meeting briefing feature (LeadBrief) lives in a separate app (backend/leadlens
        // + the Chrome extension) that does not check per-user permissions yet - it currently gates
        // only on a shared bearer token. This entry models which of a tenant's own roles are meant
        // to see a lead's briefing, ready for that app to check once it integrates against this
        // catalogue; it is not yet enforced anywhere on its own.
        catalog.put("ai-briefing", List.of("view"));

        CATALOG = Collections.unmodifiableMap(catalog);

        Set<String> all = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : catalog.entrySet()) {
            String resource = entry.getKey();
            for (String action : entry.getValue()) {
                all.add(action + ":" + resource);
            }
        }
        ALL = Collections.unmodifiableSet(all);
    }

    public static boolean isValid(String permission) {
        return ALL.contains(permission);
    }

    /** Builds the {@code action:resource} string this catalogue and every guard use. */
    public static String of(String action, String resource) {
        return action + ":" + resource;
    }

    private CrmPermission() {
    }
}
