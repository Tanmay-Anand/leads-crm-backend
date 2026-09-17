package com.leadrat.crm.leads.api.tenant;

import com.leadrat.crm.leads.api.exception.LeadratException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Establishes the tenant for the request, and rejects a tenant-scoped request that has none.
 *
 * <p>Two sources, in order: the {@code x-tenant-id} header, then the JWT. The header exists
 * because a platform user works across tenants and has no tenant claim of their own.
 */
@Slf4j
@Order(1)
@Component
public class TenantFilter extends OncePerRequestFilter {

    private final TenantAware tenantAware;
    private final HandlerExceptionResolver resolver;

    public TenantFilter(TenantAware tenantAware,
                        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.tenantAware = tenantAware;
        this.resolver = resolver;
    }

    /** Ant-style patterns for endpoints that are not tenant-scoped. */
    private final List<String> excludedPaths = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**");

    private final PathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = getRequestPath(request);

        try {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken) {
                chain.doFilter(request, response);
                return;
            }

            String headerTenantId = request.getHeader("x-tenant-id");
            if (headerTenantId != null && !headerTenantId.isBlank()) {
                try {
                    UUID tenantId = UUID.fromString(headerTenantId);
                    TenantContext.setCurrentTenant(tenantId);
                    log.debug("Set tenant context from header: {}", tenantId);
                } catch (IllegalArgumentException e) {
                    resolver.resolveException(request, response, null,
                            new LeadratException("Invalid tenant ID format. Must be a valid UUID.",
                                    HttpStatus.BAD_REQUEST));
                    return;
                }
            }

            if (isExcluded(path)) {
                chain.doFilter(request, response);
                return;
            }

            boolean isPlatform = TenantAware.isPlatform(SecurityContextHolder.getContext().getAuthentication());

            if (TenantContext.getCurrentTenant() == null && !isPlatform) {
                // TenantAware sets TenantContext as a side effect when it resolves from the JWT.
                UUID jwtTenantId = tenantAware.getTenantId();
                if (jwtTenantId == null) {
                    resolver.resolveException(request, response, null,
                            new LeadratException(
                                    "Tenant context is required. Provide the x-tenant-id header, or ensure tenantId is in your token.",
                                    HttpStatus.PRECONDITION_FAILED));
                    return;
                }
            }

            chain.doFilter(request, response);
        } finally {
            // Thread pools are reused; a leaked tenant would serve one tenant another one's rows.
            TenantContext.clear();
        }
    }

    private String getRequestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean isExcluded(String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : excludedPaths) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
