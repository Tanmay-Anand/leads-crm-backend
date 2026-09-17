package com.leadrat.crm.leads.api.tenant;

import jakarta.persistence.EntityManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;

/**
 * Picks the repository base per entity: {@link TenantAwareRepositoryImpl} (soft delete) for
 * tenant-scoped aggregate roots, the stock {@link SimpleJpaRepository} for everything else
 * (for example {@code LeadSequence}, which is a counter rather than an aggregate).
 */
public class TenantAwareRepositoryFactory extends JpaRepositoryFactory {

    public TenantAwareRepositoryFactory(EntityManager entityManager) {
        super(entityManager);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected @NotNull JpaRepositoryImplementation<?, ?> getTargetRepository(
            RepositoryInformation information, @NotNull EntityManager entityManager) {

        Class<?> domainClass = information.getDomainType();
        JpaEntityInformation<?, ?> entityInformation = getEntityInformation(domainClass);

        if (TenantAwareAggregateRoot.class.isAssignableFrom(domainClass)) {
            return new TenantAwareRepositoryImpl(entityInformation, entityManager);
        }

        return new SimpleJpaRepository(entityInformation, entityManager);
    }

    @Override
    protected @NotNull Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        Class<?> domainClass = metadata.getDomainType();

        if (TenantAwareAggregateRoot.class.isAssignableFrom(domainClass)) {
            return TenantAwareRepositoryImpl.class;
        }

        return SimpleJpaRepository.class;
    }
}
