package com.leadrat.crm.leads.api.auth.annotations;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A platform-level caller (PLATFORM_ADMIN or PLATFORM_USER), tenant roles excluded. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN','ROLE_PLATFORM_ADMIN','PLATFORM_USER','ROLE_PLATFORM_USER')")
public @interface PlatformOnly {
}
