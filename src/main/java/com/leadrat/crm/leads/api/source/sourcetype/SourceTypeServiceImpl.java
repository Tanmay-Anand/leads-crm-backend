package com.leadrat.crm.leads.api.source.sourcetype;

import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.seeding.TenantSeedingService;
import com.leadrat.crm.leads.api.source.sourcetype.dto.SourceTypeRequest;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SourceTypeServiceImpl implements SourceTypeService {

    private final CustomSourceTypeRepository sourceTypeRepository;
    private final TenantSeedingService tenantSeedingService;
    private final TenantAware tenantAware;

    @Override
    @Transactional(readOnly = true)
    public List<CustomSourceType> getAll(UUID parentId) {
        UUID tenantId = requireTenant();
        tenantSeedingService.ensureSeeded(tenantId);
        return parentId != null
                ? sourceTypeRepository.findByTenantAndParentIdAndIsActiveTrueOrderByNameAsc(tenantId, parentId)
                : sourceTypeRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomSourceType getById(UUID id) {
        CustomSourceType type = sourceTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Source type not found: " + id));
        tenantAware.validate(type);
        return type;
    }

    @Override
    @Transactional
    public CustomSourceType add(SourceTypeRequest request) {
        UUID tenantId = requireTenant();
        UUID userId = currentUserId();

        sourceTypeRepository.findFirstByTenantAndNameAndIsActiveTrue(tenantId, request.name())
                .ifPresent(existing -> {
                    throw new LeadratException("A source type named " + request.name() + " already exists.",
                            HttpStatus.CONFLICT);
                });

        CustomSourceType type = new CustomSourceType();
        type.tenant(tenantId);
        type.setName(request.name());
        type.setDisplayName(request.displayName() != null ? request.displayName() : request.name());
        type.setColorCode(request.colorCode());
        type.setParentId(request.parentId());
        type.setCreatedByUserId(userId);
        type.setLastModifiedByUserId(userId);
        return sourceTypeRepository.save(type);
    }

    @Override
    @Transactional
    public CustomSourceType update(UUID id, SourceTypeRequest request) {
        CustomSourceType type = getById(id);
        type.setName(request.name());
        if (request.displayName() != null) {
            type.setDisplayName(request.displayName());
        }
        if (request.colorCode() != null) {
            type.setColorCode(request.colorCode());
        }
        if (request.parentId() != null) {
            type.setParentId(request.parentId());
        }
        type.setLastModifiedByUserId(currentUserId());
        return sourceTypeRepository.save(type);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CustomSourceType type = getById(id);
        type.setDeletedOn(LocalDateTime.now());
        type.setDeletedByUserId(currentUserId());
        sourceTypeRepository.save(type);
        sourceTypeRepository.delete(type);
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
