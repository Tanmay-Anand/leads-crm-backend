package com.leadrat.crm.leads.api.rbac;

import com.leadrat.crm.leads.api.rbac.dto.RoleRequest;
import com.leadrat.crm.leads.api.rbac.dto.RoleResponse;

import java.util.List;
import java.util.UUID;

public interface RoleService {

    PermissionCatalogResponse getCatalog();

    List<RoleResponse> getRoles();

    RoleResponse getRole(UUID id);

    RoleResponse createRole(RoleRequest request);

    RoleResponse updateRole(UUID id, RoleRequest request);

    void deleteRole(UUID id);
}
