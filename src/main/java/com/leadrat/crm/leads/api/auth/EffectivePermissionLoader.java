package com.leadrat.crm.leads.api.auth;

import com.leadrat.crm.leads.api.rbac.Role;
import com.leadrat.crm.leads.api.rbac.RoleRepository;
import com.leadrat.crm.leads.api.user.User;
import com.leadrat.crm.leads.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reads what a non-admin user's custom role actually grants, capped at their JWT-group ceiling.
 *
 * <p>Its own {@code @Transactional(readOnly = true)} matters more than it looks: {@code
 * TenantFilterAspect} is {@code @Order(20)} while the transaction advisor runs at {@code
 * LOWEST_PRECEDENCE}, so the aspect's session-filter setup happens <em>before</em> a transaction
 * exists on whatever thread calls it. Every other call site in this app is already inside a
 * {@code @Transactional} service method, so this has never mattered before - but a permission
 * check invoked from {@code @PreAuthorize} has no surrounding transaction at all. Without this
 * class's own transaction boundary, the tenant filter would silently not apply to these reads.
 * Doubly mitigated: this transaction, and {@link UserRepository#findByIdAndTenant}/{@link
 * RoleRepository#findByIdAndTenant} being explicitly tenant-qualified so correctness never
 * depends on the aspect having run at all.
 */
@Service
@RequiredArgsConstructor
public class EffectivePermissionLoader {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<String> load(UUID tenantId, UUID userId) {
        User user = userRepository.findByIdAndTenant(userId, tenantId).orElse(null);
        if (user == null || user.getRole() == null) {
            return List.of();
        }

        List<String> ceiling = PermissionService.deriveDefaultPermissions(user.getRole());

        if (!user.isEnabled() || user.getCustomRoleId() == null) {
            return ceiling;
        }

        Role role = roleRepository.findByIdAndTenant(user.getCustomRoleId(), tenantId).orElse(null);
        if (role == null || role.getPermissions() == null) {
            return ceiling;
        }

        // Layer 2 can only subtract from Layer 1, never add - the invariant this whole class
        // exists to enforce. A custom role holding a permission its holder's JWT group would
        // never default to (e.g. TENANT_USER with a role containing delete:leads) is filtered
        // out here, not merely hidden in the UI.
        Set<String> allowed = new HashSet<>(ceiling);
        return role.getPermissions().stream().filter(allowed::contains).toList();
    }
}
