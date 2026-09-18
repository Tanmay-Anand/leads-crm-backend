package com.leadrat.crm.leads.api.rbac;

import com.leadrat.crm.leads.api.exception.LeadratException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** Rejects a role's permission list unless every entry is a real, enforced permission. */
@Component
public class PermissionValidator {

    public void validate(List<String> permissions) {
        if (permissions == null) {
            throw new LeadratException("Permissions list must not be null", HttpStatus.BAD_REQUEST);
        }
        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                throw new LeadratException("Permission strings must not be null or blank", HttpStatus.BAD_REQUEST);
            }
            if (!CrmPermission.isValid(permission)) {
                throw new LeadratException(
                        "Unknown permission: '" + permission + "'. "
                                + "Use GET /permissions/catalog to retrieve valid permissions.",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }
}
