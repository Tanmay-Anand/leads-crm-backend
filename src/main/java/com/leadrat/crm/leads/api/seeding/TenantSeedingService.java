package com.leadrat.crm.leads.api.seeding;

import com.leadrat.crm.leads.api.auth.PermissionService;
import com.leadrat.crm.leads.api.core.UserRole;
import com.leadrat.crm.leads.api.leadstatus.LeadStatus;
import com.leadrat.crm.leads.api.leadstatus.LeadStatusRepository;
import com.leadrat.crm.leads.api.rbac.Role;
import com.leadrat.crm.leads.api.rbac.RoleRepository;
import com.leadrat.crm.leads.api.source.sourcecategory.CustomSourceCategory;
import com.leadrat.crm.leads.api.source.sourcecategory.CustomSourceCategoryRepository;
import com.leadrat.crm.leads.api.source.sourcetype.CustomSourceType;
import com.leadrat.crm.leads.api.source.sourcetype.CustomSourceTypeRepository;
import com.leadrat.crm.leads.api.tag.CustomTag;
import com.leadrat.crm.leads.api.tag.CustomTagRepository;
import com.leadrat.crm.leads.api.temperature.CustomTemperature;
import com.leadrat.crm.leads.api.temperature.CustomTemperatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seeds a tenant with the master data the lead module cannot function without: statuses,
 * temperatures, tags and the source taxonomy.
 *
 * <p>The reference service provisions a tenant up front, off an SQS message, and self-heals at
 * startup. There is no provisioning queue here, so seeding is lazy instead: the first read on any
 * master-data service calls {@link #ensureSeeded}. Idempotent, so calling it on every request is
 * safe and a partially-seeded tenant repairs itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSeedingService {

    /** Author recorded for seeded rows, since no user is logged in when seeding runs. */
    public static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final CustomSourceCategoryRepository sourceCategoryRepository;
    private final CustomSourceTypeRepository sourceTypeRepository;
    private final CustomTemperatureRepository temperatureRepository;
    private final CustomTagRepository tagRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final RoleRepository roleRepository;

    /**
     * Seeds the tenant if anything is missing.
     *
     * <p>Runs in its own transaction so it can write even when called from a read-only one, which
     * is the common case: the first {@code getAll()} a new tenant makes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureSeeded(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        boolean needsSeeding = sourceCategoryRepository.findByTenant(tenantId).isEmpty()
                || temperatureRepository.findByTenant(tenantId).isEmpty()
                || tagRepository.findByTenant(tenantId).isEmpty()
                || leadStatusRepository.findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(tenantId).isEmpty()
                || roleRepository.findByTenant(tenantId).isEmpty();

        if (needsSeeding) {
            log.info("[TenantSeeding] Seeding defaults for tenant {}", tenantId);
            seedDefaults(tenantId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedDefaults(UUID tenantId) {
        seedLeadStatuses(tenantId);
        seedTemperatures(tenantId);
        seedTags(tenantId);
        seedSourceTaxonomy(tenantId);
        seedDefaultRoles(tenantId);
        log.info("[TenantSeeding] Done for tenant {}", tenantId);
    }

    // ─── Default roles ────────────────────────────────────────────────────────

    /**
     * Seeds the four system roles - existing tenants get them free on the next master-data
     * read, no backfill script. Each named role's permission set is the same ceiling {@link
     * PermissionService#deriveDefaultPermissions} computes for that {@link UserRole} at check
     * time; seeding it as a real, visible {@link Role} row lets a tenant admin see and start
     * from the default rather than only ever seeing custom roles in the list.
     */
    private void seedDefaultRoles(UUID tenantId) {
        if (!roleRepository.findByTenant(tenantId).isEmpty()) {
            return;
        }
        saveSystemRole(tenantId, "Tenant Admin", PermissionService.deriveDefaultPermissions(UserRole.TENANT_ADMIN));
        saveSystemRole(tenantId, "Tenant User", PermissionService.deriveDefaultPermissions(UserRole.TENANT_USER));
        saveSystemRole(tenantId, "Platform Admin", PermissionService.deriveDefaultPermissions(UserRole.PLATFORM_ADMIN));
        saveSystemRole(tenantId, "Platform User", PermissionService.deriveDefaultPermissions(UserRole.PLATFORM_USER));
    }

    private void saveSystemRole(UUID tenantId, String name, List<String> permissions) {
        if (roleRepository.existsByTenantAndNameIgnoreCase(tenantId, name)) {
            return;
        }
        Role role = new Role();
        role.tenant(tenantId);
        role.setName(name);
        role.setPermissions(permissions);
        role.setSystem(true);
        roleRepository.save(role);
    }

    // ─── Lead statuses ────────────────────────────────────────────────────────

    private void seedLeadStatuses(UUID tenantId) {
        if (!leadStatusRepository.findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(tenantId).isEmpty()) {
            return;
        }
        // The first one is the default: a lead created before anyone configures the pipeline still
        // lands somewhere, rather than on a null status the list screen cannot group.
        saveStatus(tenantId, "new", "New", "#2196F3", 0, true);
        saveStatus(tenantId, "contacted", "Contacted", "#4CAF50", 1, false);
        saveStatus(tenantId, "site_visit", "Site Visit", "#FF9800", 2, false);
        saveStatus(tenantId, "negotiation", "Negotiation", "#9C27B0", 3, false);
        saveStatus(tenantId, "won", "Won", "#00897B", 4, false);
        saveStatus(tenantId, "lost", "Lost", "#E53935", 5, false);
    }

    private void saveStatus(UUID tenantId, String name, String displayName, String colorCode,
                            int order, boolean isDefault) {
        LeadStatus status = new LeadStatus();
        status.tenant(tenantId);
        status.setName(name);
        status.setDisplayName(displayName);
        status.setColorCode(colorCode);
        status.setDisplayOrder(order);
        status.setDefault(isDefault);
        status.setCreatedByUserId(SYSTEM_USER);
        status.setLastModifiedByUserId(SYSTEM_USER);
        leadStatusRepository.save(status);
    }

    // ─── Temperatures ─────────────────────────────────────────────────────────

    private void seedTemperatures(UUID tenantId) {
        if (!temperatureRepository.findByTenant(tenantId).isEmpty()) {
            return;
        }
        saveTemperature(tenantId, "hot", "Hot", "#E53935");
        saveTemperature(tenantId, "warm", "Warm", "#FB8C00");
        saveTemperature(tenantId, "cold", "Cold", "#1E88E5");
    }

    private void saveTemperature(UUID tenantId, String name, String displayName, String colorCode) {
        CustomTemperature temperature = new CustomTemperature();
        temperature.tenant(tenantId);
        temperature.setName(name);
        temperature.setDisplayName(displayName);
        temperature.setColorCode(colorCode);
        temperature.setCreatedByUserId(SYSTEM_USER);
        temperature.setLastModifiedByUserId(SYSTEM_USER);
        temperatureRepository.save(temperature);
    }

    // ─── Tags ─────────────────────────────────────────────────────────────────

    private void seedTags(UUID tenantId) {
        if (!tagRepository.findByTenant(tenantId).isEmpty()) {
            return;
        }
        saveTag(tenantId, "high_budget", "High Budget", "#6A1B9A");
        saveTag(tenantId, "investor", "Investor", "#00838F");
        saveTag(tenantId, "nri", "NRI", "#EF6C00");
        saveTag(tenantId, "urgent", "Urgent", "#C62828");
    }

    private void saveTag(UUID tenantId, String name, String displayName, String colorCode) {
        CustomTag tag = new CustomTag();
        tag.tenant(tenantId);
        tag.setName(name);
        tag.setDisplayName(displayName);
        tag.setColorCode(colorCode);
        tag.setCreatedByUserId(SYSTEM_USER);
        tag.setLastModifiedByUserId(SYSTEM_USER);
        tagRepository.save(tag);
    }

    // ─── Source taxonomy ──────────────────────────────────────────────────────

    private void seedSourceTaxonomy(UUID tenantId) {
        List<CustomSourceCategory> existing = sourceCategoryRepository.findByTenant(tenantId);
        CustomSourceCategory digital;
        CustomSourceCategory portal;

        if (existing.isEmpty()) {
            digital = saveCategory(tenantId, "digital", "Digital", "#1976D2");
            portal = saveCategory(tenantId, "portal", "Portal", "#388E3C");
            saveCategory(tenantId, "walk_in", "Walk-in", "#F57C00");
            saveCategory(tenantId, "referral", "Referral", "#7B1FA2");
            saveCategory(tenantId, "channel_partner", "Channel Partner", "#C62828");
        } else {
            digital = findByName(existing, "digital");
            portal = findByName(existing, "portal");
        }

        if (!sourceTypeRepository.findByTenant(tenantId).isEmpty()) {
            return;
        }

        UUID digitalId = digital != null ? digital.getId() : null;
        UUID portalId = portal != null ? portal.getId() : null;

        saveType(tenantId, "facebook", "Facebook", "#1877F2", digitalId);
        saveType(tenantId, "google_ads", "Google Ads", "#EA4335", digitalId);
        saveType(tenantId, "instagram", "Instagram", "#C13584", digitalId);
        saveType(tenantId, "website", "Website", "#455A64", digitalId);
        saveType(tenantId, "housing", "Housing.com", "#EF5350", portalId);
        saveType(tenantId, "magicbricks", "MagicBricks", "#D32F2F", portalId);
        saveType(tenantId, "ninety_nine_acres", "99acres", "#1565C0", portalId);
    }

    private CustomSourceCategory findByName(List<CustomSourceCategory> categories, String name) {
        return categories.stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElse(null);
    }

    private CustomSourceCategory saveCategory(UUID tenantId, String name, String displayName, String colorCode) {
        CustomSourceCategory category = new CustomSourceCategory();
        category.tenant(tenantId);
        category.setName(name);
        category.setDisplayName(displayName);
        category.setColorCode(colorCode);
        category.setCreatedByUserId(SYSTEM_USER);
        category.setLastModifiedByUserId(SYSTEM_USER);
        return sourceCategoryRepository.save(category);
    }

    private void saveType(UUID tenantId, String name, String displayName, String colorCode, UUID parentId) {
        CustomSourceType type = new CustomSourceType();
        type.tenant(tenantId);
        type.setName(name);
        type.setDisplayName(displayName);
        type.setColorCode(colorCode);
        type.setParentId(parentId);
        type.setCreatedByUserId(SYSTEM_USER);
        type.setLastModifiedByUserId(SYSTEM_USER);
        sourceTypeRepository.save(type);
    }
}
