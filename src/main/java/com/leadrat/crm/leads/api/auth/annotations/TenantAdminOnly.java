package com.leadrat.crm.leads.api.auth.annotations;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A TENANT_ADMIN caller only. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyAuthority('TENANT_ADMIN','ROLE_TENANT_ADMIN')")
public @interface TenantAdminOnly {
}
