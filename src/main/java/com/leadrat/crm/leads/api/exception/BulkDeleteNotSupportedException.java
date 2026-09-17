package com.leadrat.crm.leads.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown by {@code TenantAwareRepositoryImpl} for every bulk delete.
 *
 * <p>Deletion is a soft delete that has to run per entity, so a bulk variant would silently
 * hard-delete rows the tenant filter never scoped.
 */
public class BulkDeleteNotSupportedException extends LeadratException {

    public BulkDeleteNotSupportedException() {
        super("Bulk delete is not supported. Delete entities one at a time.", HttpStatus.BAD_REQUEST);
    }
}
