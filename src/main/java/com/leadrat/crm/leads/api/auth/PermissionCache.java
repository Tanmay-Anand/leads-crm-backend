package com.leadrat.crm.leads.api.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Held directly, not through {@code spring-boot-starter-cache}/{@code @Cacheable}: eviction
 * needs "every user in this tenant" after a role edit, and Spring's {@code Cache} abstraction
 * only exposes {@code evict(key)}/{@code clear()} - no prefix eviction.
 */
@Component
public class PermissionCache {

    private static final int MAX_SIZE = 10_000;
    private static final Duration TTL = Duration.ofSeconds(60);

    private final Cache<String, List<String>> cache = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .expireAfterWrite(TTL)
            .build();

    public List<String> get(UUID tenantId, UUID userId, Supplier<List<String>> loader) {
        return cache.get(key(tenantId, userId), k -> loader.get());
    }

    public void evictUser(UUID tenantId, UUID userId) {
        cache.invalidate(key(tenantId, userId));
    }

    public void evictTenant(UUID tenantId) {
        String prefix = tenantId + "|";
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String key(UUID tenantId, UUID userId) {
        return tenantId + "|" + userId;
    }
}
