package com.leadrat.crm.leads.api.auth.annotations;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Any authenticated caller, regardless of role - for endpoints with no resource permission of
 * their own (e.g. {@code /users/me}, {@code /permissions/catalog}, the various {@code /*enums}
 * endpoints).
 *
 * <p>Exactly one guard per handler method - never combine this with a permission expression on
 * the same method (see {@code PermissionService}'s class doc for why).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("isAuthenticated()")
public @interface AuthenticatedOnly {
}
