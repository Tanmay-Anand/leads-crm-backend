package com.leadrat.crm.leads.api.tenant;

import jakarta.persistence.EntityManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import java.io.Serializable;

/** Wires {@link TenantAwareRepositoryFactory} in, via {@code JpaConfiguration}. */
public class TenantAwareRepositoryFactoryBean<R extends JpaRepository<T, ID>, T, ID extends Serializable>
        extends JpaRepositoryFactoryBean<R, T, ID> {

    public TenantAwareRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    @Override
    protected @NotNull RepositoryFactorySupport createRepositoryFactory(@NotNull EntityManager entityManager) {
        return new TenantAwareRepositoryFactory(entityManager);
    }
}
