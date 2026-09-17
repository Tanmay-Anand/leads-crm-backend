package com.leadrat.crm.leads.api.temperature;

import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.seeding.TenantSeedingService;
import com.leadrat.crm.leads.api.temperature.dto.TemperatureRequest;
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
public class TemperatureServiceImpl implements TemperatureService {

    private final CustomTemperatureRepository temperatureRepository;
    private final TenantSeedingService tenantSeedingService;
    private final TenantAware tenantAware;

    @Override
    @Transactional(readOnly = true)
    public List<CustomTemperature> getAll() {
        UUID tenantId = requireTenant();
        tenantSeedingService.ensureSeeded(tenantId);
        return temperatureRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomTemperature getById(UUID id) {
        CustomTemperature temperature = temperatureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Temperature not found: " + id));
        tenantAware.validate(temperature);
        return temperature;
    }

    @Override
    @Transactional
    public CustomTemperature add(TemperatureRequest request) {
        UUID tenantId = requireTenant();
        UUID userId = currentUserId();

        temperatureRepository.findFirstByTenantAndNameAndIsActiveTrue(tenantId, request.name())
                .ifPresent(existing -> {
                    throw new LeadratException("A temperature named " + request.name() + " already exists.",
                            HttpStatus.CONFLICT);
                });

        CustomTemperature temperature = new CustomTemperature();
        temperature.tenant(tenantId);
        temperature.setName(request.name());
        temperature.setDisplayName(request.displayName() != null ? request.displayName() : request.name());
        temperature.setColorCode(request.colorCode());
        temperature.setCreatedByUserId(userId);
        temperature.setLastModifiedByUserId(userId);
        return temperatureRepository.save(temperature);
    }

    @Override
    @Transactional
    public CustomTemperature update(UUID id, TemperatureRequest request) {
        CustomTemperature temperature = getById(id);
        temperature.setName(request.name());
        if (request.displayName() != null) {
            temperature.setDisplayName(request.displayName());
        }
        if (request.colorCode() != null) {
            temperature.setColorCode(request.colorCode());
        }
        temperature.setLastModifiedByUserId(currentUserId());
        return temperatureRepository.save(temperature);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CustomTemperature temperature = getById(id);
        temperature.setDeletedOn(LocalDateTime.now());
        temperature.setDeletedByUserId(currentUserId());
        temperatureRepository.save(temperature);
        temperatureRepository.delete(temperature);
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
