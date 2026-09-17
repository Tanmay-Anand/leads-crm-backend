package com.leadrat.crm.leads.api.tenant;

import com.leadrat.crm.leads.api.exception.BulkDeleteNotSupportedException;
import jakarta.persistence.EntityManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Repository base that turns {@code delete} into a soft delete and refuses every bulk variant.
 */
public class TenantAwareRepositoryImpl<T extends TenantAwareAggregateRoot<T>>
        extends SimpleJpaRepository<T, UUID> {

    private final EntityManager entityManager;

    public TenantAwareRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void delete(@NotNull T entity) {
        entity.softDelete();
        entityManager.merge(entity);
    }

    @Override
    @Transactional
    public void deleteById(@NotNull UUID id) {
        findById(id).ifPresent(this::delete);
    }

    @Override
    @Transactional
    public void deleteAll(@NotNull Iterable<? extends T> entities) {
        throw new BulkDeleteNotSupportedException();
    }

    @Override
    @Transactional
    public void deleteAll() {
        throw new BulkDeleteNotSupportedException();
    }

    @Override
    @Transactional
    public void deleteAllById(@NotNull Iterable<? extends UUID> ids) {
        throw new BulkDeleteNotSupportedException();
    }

    @Override
    @Transactional
    public void deleteAllInBatch(@NotNull Iterable<T> entities) {
        throw new BulkDeleteNotSupportedException();
    }

    @Override
    @Transactional
    public void deleteAllInBatch() {
        throw new BulkDeleteNotSupportedException();
    }

    @Override
    @Transactional
    public void deleteAllByIdInBatch(@NotNull Iterable<UUID> ids) {
        throw new BulkDeleteNotSupportedException();
    }
}
