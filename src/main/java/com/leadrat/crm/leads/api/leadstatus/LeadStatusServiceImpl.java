package com.leadrat.crm.leads.api.leadstatus;

import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.lead.Lead;
import com.leadrat.crm.leads.api.lead.LeadRepository;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusReorderRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusUpdateRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusUsageDto;
import com.leadrat.crm.leads.api.search.SearchResource;
import com.leadrat.crm.leads.api.search.TenantAwareSearchSpecificationBuilder;
import com.leadrat.crm.leads.api.seeding.TenantSeedingService;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadStatusServiceImpl implements LeadStatusService {

    private final LeadStatusRepository leadStatusRepository;
    private final LeadRepository leadRepository;
    private final TenantSeedingService tenantSeedingService;
    private final TenantAware tenantAware;

    @Override
    @Transactional(readOnly = true)
    public Page<LeadStatus> getAll(Pageable pageable) {
        UUID tenantId = requireTenant();
        tenantSeedingService.ensureSeeded(tenantId);
        return leadStatusRepository.findByTenant(tenantId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeadStatus> search(List<SearchResource> resources, Pageable pageable) {
        UUID tenantId = requireTenant();
        return leadStatusRepository.findAll(
                new TenantAwareSearchSpecificationBuilder<LeadStatus>(resources, tenantId).build(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadStatus getById(UUID id) {
        LeadStatus status = leadStatusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lead status not found: " + id));
        tenantAware.validate(status);
        return status;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadStatus> getNames() {
        UUID tenantId = requireTenant();
        tenantSeedingService.ensureSeeded(tenantId);
        return leadStatusRepository.findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(tenantId);
    }

    @Override
    @Transactional
    public LeadStatus add(LeadStatusRequest request) {
        UUID tenantId = requireTenant();
        UUID userId = currentUserId();

        leadStatusRepository.findFirstByTenantAndNameAndIsActiveTrue(tenantId, request.name())
                .ifPresent(existing -> {
                    throw new LeadratException("A lead status named " + request.name() + " already exists.",
                            HttpStatus.CONFLICT);
                });

        LeadStatus status = new LeadStatus();
        status.tenant(tenantId);
        status.setName(request.name());
        status.setDisplayName(request.displayName() != null ? request.displayName() : request.name());
        status.setColorCode(request.colorCode());
        status.setNoteRequired(Boolean.TRUE.equals(request.isNoteRequired()));
        status.setDisplayOrder(request.displayOrder() != null
                ? request.displayOrder()
                : (int) leadStatusRepository.countByTenantAndIsActiveTrue(tenantId));
        status.setCreatedByUserId(userId);
        status.setLastModifiedByUserId(userId);

        applyDefaultFlag(status, Boolean.TRUE.equals(request.isDefault()), tenantId);

        return leadStatusRepository.save(status);
    }

    @Override
    @Transactional
    public LeadStatus update(UUID id, LeadStatusUpdateRequest request) {
        LeadStatus status = getById(id);
        UUID tenantId = requireTenant();

        if (request.name() != null) {
            status.setName(request.name());
        }
        if (request.displayName() != null) {
            status.setDisplayName(request.displayName());
        }
        if (request.colorCode() != null) {
            status.setColorCode(request.colorCode());
        }
        if (request.isNoteRequired() != null) {
            status.setNoteRequired(request.isNoteRequired());
        }
        if (request.displayOrder() != null) {
            status.setDisplayOrder(request.displayOrder());
        }
        if (request.isDefault() != null) {
            applyDefaultFlag(status, request.isDefault(), tenantId);
        }

        status.setLastModifiedByUserId(currentUserId());
        return leadStatusRepository.save(status);
    }

    @Override
    @Transactional
    public List<LeadStatus> reorder(LeadStatusReorderRequest request) {
        UUID tenantId = requireTenant();
        List<UUID> orderedIds = request.orderedIds();

        for (int i = 0; i < orderedIds.size(); i++) {
            LeadStatus status = getById(orderedIds.get(i));
            status.setDisplayOrder(i);
            status.setLastModifiedByUserId(currentUserId());
            leadStatusRepository.save(status);
        }

        return leadStatusRepository.findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadStatusUsageDto usage(UUID id) {
        UUID tenantId = requireTenant();
        getById(id);
        return new LeadStatusUsageDto(id, leadRepository.countByTenantAndStatusIdAndIsActiveTrue(tenantId, id));
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID targetStatusId) {
        UUID tenantId = requireTenant();
        LeadStatus status = getById(id);

        // Leads keep a statusId rather than a join, so deleting a status out from under them would
        // leave rows pointing at nothing. Migrate first, then delete.
        List<Lead> affected = leadRepository.findActiveByTenantAndStatusId(tenantId, id);
        if (!affected.isEmpty()) {
            if (targetStatusId == null) {
                throw new LeadratException(
                        affected.size() + " active leads are on this status. Supply targetStatusId to move them.",
                        HttpStatus.PRECONDITION_FAILED);
            }
            LeadStatus target = getById(targetStatusId);
            if (target.getId().equals(status.getId())) {
                throw new LeadratException("targetStatusId must differ from the status being deleted.",
                        HttpStatus.BAD_REQUEST);
            }
            affected.forEach(lead -> {
                lead.setStatusId(target.getId());
                lead.setLastModifiedByUserId(currentUserId());
            });
            leadRepository.saveAll(affected);
            log.info("Moved {} leads from status {} to {}", affected.size(), id, targetStatusId);
        }

        status.setDeletedOn(LocalDateTime.now());
        status.setDeletedByUserId(currentUserId());
        leadStatusRepository.save(status);
        leadStatusRepository.delete(status);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** At most one default per tenant, so setting one clears the incumbent. */
    private void applyDefaultFlag(LeadStatus status, boolean makeDefault, UUID tenantId) {
        if (!makeDefault) {
            status.setDefault(false);
            return;
        }
        leadStatusRepository.findFirstByTenantAndIsDefaultTrueAndIsActiveTrue(tenantId)
                .filter(existing -> !existing.getId().equals(status.getId()))
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    leadStatusRepository.save(existing);
                });
        status.setDefault(true);
    }

    private UUID requireTenant() {
        UUID tenantId = tenantAware.getTenantId();
        if (tenantId == null) {
            throw new LeadratException("Tenant context is required for this operation",
                    HttpStatus.PRECONDITION_FAILED);
        }
        return tenantId;
    }

    private UUID currentUserId() {
        UUID userId = tenantAware.getLoggedInUserId();
        return userId != null ? userId : TenantSeedingService.SYSTEM_USER;
    }
}
