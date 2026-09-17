package com.leadrat.crm.leads.api.lead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Generates unique, sequential lead codes.
 *
 * <p>Runs in a new transaction so the increment commits immediately. A code is consumed even when
 * the surrounding create rolls back, which leaves a gap in the sequence but never a duplicate. The
 * pessimistic lock serialises concurrent callers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadSequenceServiceImpl implements LeadSequenceService {

    private final LeadSequenceRepository leadSequenceRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextCode(UUID tenantId) {
        LeadSequence seq = leadSequenceRepository.findByTenantIdWithLock(tenantId)
                .orElseGet(() -> leadSequenceRepository.save(new LeadSequence(tenantId)));

        long next = seq.getLastValue() + 1;
        seq.setLastValue(next);
        leadSequenceRepository.save(seq);

        String code = "LD-" + String.format("%06d", next);
        log.debug("Generated lead code {} for tenant {}", code, tenantId);
        return code;
    }
}
