package com.leadrat.crm.leads.api.rbac;

import com.leadrat.crm.leads.api.auth.annotations.AuthenticatedOnly;
import com.leadrat.crm.leads.api.rbac.dto.RoleRequest;
import com.leadrat.crm.leads.api.rbac.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Tenant-defined custom roles and the permission catalogue")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "Permission catalogue",
            description = "Every action:resource this backend enforces. The permission-matrix editor renders "
                    + "from this, never from a hardcoded list, so a backend addition becomes assignable immediately.")
    @GetMapping("/permissions/catalog")
    @AuthenticatedOnly
    public ResponseEntity<PermissionCatalogResponse> getCatalog() {
        return ResponseEntity.ok(roleService.getCatalog());
    }

    @Operation(summary = "List roles for this tenant")
    @GetMapping("/roles")
    @PreAuthorize("@permissionService.check('view', 'roles')")
    public ResponseEntity<List<RoleResponse>> getRoles() {
        return ResponseEntity.ok(roleService.getRoles());
    }

    @Operation(summary = "Get a role by ID")
    @GetMapping("/roles/{id}")
    @PreAuthorize("@permissionService.check('view', 'roles')")
    public ResponseEntity<RoleResponse> getRole(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.getRole(id));
    }

    @Operation(summary = "Create a custom role")
    @PostMapping("/roles")
    @PreAuthorize("@permissionService.check('add', 'roles')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(request));
    }

    @Operation(summary = "Update a custom role", description = "Refused with 403 for a system role.")
    @PutMapping("/roles/{id}")
    @PreAuthorize("@permissionService.check('update', 'roles')")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @Operation(summary = "Delete (soft) a custom role", description = "Refused with 403 for a system role.")
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("@permissionService.check('delete', 'roles')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
