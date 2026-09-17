package com.leadrat.crm.leads.api.tenant;

import jakarta.persistence.PrePersist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stamps the current tenant onto a {@link TenantAwareAggregateRoot} on insert.
 *
 * <p>The service layer is the primary path; this is the net that stops a future write path from
 * persisting a row with a null tenant, which the tenant filter would then hide from everyone.
 * Skips when the tenant is already set, so an explicit {@code tenant(...)} always wins.
 */
@Slf4j
@Component
public class TenantAwareEntityListener implements ApplicationContextAware {

    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) {
        synchronized (TenantAwareEntityListener.class) {
            applicationContext = context;
        }
    }

    @PrePersist
    public void prePersist(Object entity) {
        if (!(entity instanceof TenantAwareAggregateRoot<?> tenantAwareEntity)) {
            return;
        }

        if (tenantAwareEntity.getTenant() != null) {
            log.debug("Tenant already set for {}, skipping", entity.getClass().getSimpleName());
            return;
        }

        try {
            ApplicationContext context = applicationContext;
            if (context == null) {
                log.warn("ApplicationContext not initialised; tenant ID not set");
                return;
            }

            TenantAware tenantAware = context.getBean(TenantAware.class);

            if (tenantAware.isAnonymous()) {
                log.debug("No tenant context available, skipping");
                return;
            }

            UUID tenantId = tenantAware.getTenantId();
            if (tenantId != null) {
                tenantAwareEntity.tenant(tenantId);
            }
        } catch (Exception e) {
            log.debug("Could not resolve tenant for {}: {}", entity.getClass().getSimpleName(), e.getMessage());
        }
    }
}
