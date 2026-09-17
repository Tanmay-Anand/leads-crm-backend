package com.leadrat.crm.leads.api.config;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepositoryFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableJpaRepositories(
        basePackages = "com.leadrat.crm.leads.api",
        repositoryFactoryBeanClass = TenantAwareRepositoryFactoryBean.class)
public class JpaConfiguration {

    /** Fills {@code @CreatedBy} / {@code @LastModifiedBy} from the Cognito username. */
    @Bean
    AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.of("System :: Leadrat");
            }
            String name = authentication.getName();
            return Optional.of(name != null ? name : "System :: Leadrat");
        };
    }
}
