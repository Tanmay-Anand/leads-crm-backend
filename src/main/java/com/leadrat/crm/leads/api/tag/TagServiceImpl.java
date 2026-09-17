package com.leadrat.crm.leads.api.tag;

import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.seeding.TenantSeedingService;
import com.leadrat.crm.leads.api.tag.dto.TagRequest;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final CustomTagRepository tagRepository;
    private final TenantSeedingService tenantSeedingService;
    private final TenantAware tenantAware;

    @Override
    @Transactional(readOnly = true)
    public List<CustomTag> getAll() {
        UUID tenantId = requireTenant();
        tenantSeedingService.ensureSeeded(tenantId);
        return tagRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomTag getById(UUID id) {
        CustomTag tag = tagRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tag not found: " + id));
        tenantAware.validate(tag);
        return tag;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomTag> getByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return tagRepository.findByTenantAndIdInAndIsActiveTrue(requireTenant(), ids);
    }

    @Override
    @Transactional
    public CustomTag add(TagRequest request) {
        UUID tenantId = requireTenant();
        UUID userId = currentUserId();

        tagRepository.findFirstByTenantAndNameAndIsActiveTrue(tenantId, request.name())
                .ifPresent(existing -> {
                    throw new LeadratException("A tag named " + request.name() + " already exists.",
                            HttpStatus.CONFLICT);
                });

        CustomTag tag = new CustomTag();
        tag.tenant(tenantId);
        tag.setName(request.name());
        tag.setDisplayName(request.displayName() != null ? request.displayName() : request.name());
        tag.setColorCode(request.colorCode());
        tag.setCreatedByUserId(userId);
        tag.setLastModifiedByUserId(userId);
        return tagRepository.save(tag);
    }

    @Override
    @Transactional
    public CustomTag update(UUID id, TagRequest request) {
        CustomTag tag = getById(id);
        tag.setName(request.name());
        if (request.displayName() != null) {
            tag.setDisplayName(request.displayName());
        }
        if (request.colorCode() != null) {
            tag.setColorCode(request.colorCode());
        }
        tag.setLastModifiedByUserId(currentUserId());
        return tagRepository.save(tag);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CustomTag tag = getById(id);
        tag.setDeletedOn(LocalDateTime.now());
        tag.setDeletedByUserId(currentUserId());
        tagRepository.save(tag);
        tagRepository.delete(tag);
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
