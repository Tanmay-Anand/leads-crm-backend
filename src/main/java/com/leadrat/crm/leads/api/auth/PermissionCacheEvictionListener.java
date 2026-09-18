package com.leadrat.crm.leads.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PermissionCacheEvictionListener {

    private final PermissionCache cache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPermissionsChanged(PermissionsChangedEvent event) {
        if (event.userId() != null) {
            cache.evictUser(event.tenantId(), event.userId());
        } else {
            cache.evictTenant(event.tenantId());
        }
    }
}
