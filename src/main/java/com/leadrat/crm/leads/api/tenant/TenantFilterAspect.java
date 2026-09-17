package com.leadrat.crm.leads.api.tenant;

import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.util.SystemToken;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enables the Hibernate {@code tenantFilter} before any call on a {@link TenantAwareRepository}.
 *
 * <p>This is what makes tenant isolation structural rather than something each query has to
 * remember: a repository method that forgets to name the tenant is still scoped.
 */
@Slf4j
@Aspect
@Order(20)
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    private static final String TENANT_FILTER = "tenantFilter";
    private static final String TENANT_ID_PARAM = "tenantId";

    @PersistenceContext
    private EntityManager entityManager;

    private final TenantAware tenantAware;

    @Before("execution(* com.leadrat.crm.leads.api.tenant.TenantAwareRepository+.*(..))")
    public void enableTenantFilter(JoinPoint joinPoint) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new LeadratException("Authentication required to access tenant-scoped resources",
                    HttpStatus.UNAUTHORIZED);
        }

        // Internal work carries its tenant on the token and reads across it deliberately.
        if (authentication instanceof SystemToken) {
            log.debug("SystemToken detected, skipping tenant filter");
            return;
        }

        // Platform users need cross-tenant visibility, unless they named a tenant explicitly.
        if (TenantAware.isPlatform(authentication)) {
            UUID explicitTenantId = TenantContext.getCurrentTenant();
            Session session = entityManager.unwrap(Session.class);

            if (explicitTenantId != null) {
                if (session.getEnabledFilter(TENANT_FILTER) == null) {
                    session.enableFilter(TENANT_FILTER).setParameter(TENANT_ID_PARAM, explicitTenantId.toString());
                }
                return;
            }

            if (session.getEnabledFilter(TENANT_FILTER) != null) {
                session.disableFilter(TENANT_FILTER);
            }
            return;
        }

        try {
            UUID tenantId = tenantAware.getTenantId();
            if (tenantId == null) {
                throw new LeadratException("Tenant context is required for this operation",
                        HttpStatus.PRECONDITION_FAILED);
            }

            Session session = entityManager.unwrap(Session.class);
            if (session.getEnabledFilter(TENANT_FILTER) == null) {
                session.enableFilter(TENANT_FILTER).setParameter(TENANT_ID_PARAM, tenantId.toString());
                log.debug("Enabled tenant filter with tenantId: {}", tenantId);
            }
        } catch (LeadratException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not enable tenant filter: {}", e.getMessage());
        }
    }
}
