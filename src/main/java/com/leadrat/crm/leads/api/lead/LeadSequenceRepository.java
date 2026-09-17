package com.leadrat.crm.leads.api.lead;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LeadSequenceRepository extends JpaRepository<LeadSequence, UUID> {

    /**
     * Reads the counter under a pessimistic write lock, so two concurrent creates cannot be handed
     * the same code.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM LeadSequence s WHERE s.tenantId = :tenantId")
    Optional<LeadSequence> findByTenantIdWithLock(@Param("tenantId") UUID tenantId);
}
