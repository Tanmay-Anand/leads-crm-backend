package com.leadrat.crm.leads.api.source.sourcecategory;

import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.seeding.TenantSeedingService;
import com.leadrat.crm.leads.api.source.sourcecategory.dto.SourceCategoryRequest;
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
public class SourceCategoryServiceImpl implements SourceCategoryService {

    private final CustomSourceCategoryRepository sourceCategoryRepository;
    private final TenantSeedingService tenantSeedingService;
    private final TenantAware tenantAware;

    @Override
    @Transactional(readOnly = true)
    public List<CustomSourceCategory> getAll() {
        UUID tenantId = requireTenant();
        tenantSeedingService.ensureSeeded(tenantId);
        return sourceCategoryRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomSourceCategory getById(UUID id) {
        CustomSourceCategory category = sourceCategoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Source category not found: " + id));
        tenantAware.validate(category);
        return category;
    }

    @Override
    @Transactional
    public CustomSourceCategory add(SourceCategoryRequest request) {
        UUID tenantId = requireTenant();
        UUID userId = currentUserId();

        sourceCategoryRepository.findFirstByTenantAndNameAndIsActiveTrue(tenantId, request.name())
                .ifPresent(existing -> {
                    throw new LeadratException("A source category named " + request.name() + " already exists.",
                            HttpStatus.CONFLICT);
                });

        CustomSourceCategory category = new CustomSourceCategory();
        category.tenant(tenantId);
        category.setName(request.name());
        category.setDisplayName(request.displayName() != null ? request.displayName() : request.name());
        category.setColorCode(request.colorCode());
        category.setCreatedByUserId(userId);
        category.setLastModifiedByUserId(userId);
        return sourceCategoryRepository.save(category);
    }

    @Override
    @Transactional
    public CustomSourceCategory update(UUID id, SourceCategoryRequest request) {
        CustomSourceCategory category = getById(id);
        category.setName(request.name());
        if (request.displayName() != null) {
            category.setDisplayName(request.displayName());
        }
        if (request.colorCode() != null) {
            category.setColorCode(request.colorCode());
        }
        category.setLastModifiedByUserId(currentUserId());
        return sourceCategoryRepository.save(category);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CustomSourceCategory category = getById(id);
        category.setDeletedOn(LocalDateTime.now());
        category.setDeletedByUserId(currentUserId());
        sourceCategoryRepository.save(category);
        sourceCategoryRepository.delete(category);
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
