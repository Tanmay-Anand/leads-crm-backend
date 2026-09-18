package com.leadrat.crm.leads.api.auth.annotations;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A PLATFORM_ADMIN caller only. Reserved for the highest-blast-radius actions (e.g. assigning
 *  a PLATFORM_* role to another user). */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN','ROLE_PLATFORM_ADMIN')")
public @interface PlatformAdminOnly {
}
