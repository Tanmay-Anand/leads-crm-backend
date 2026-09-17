package com.leadrat.crm.leads.api.temperature;

import com.leadrat.crm.leads.api.tenant.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomTemperatureRepository extends TenantAwareRepository<CustomTemperature> {

    List<CustomTemperature> findByTenantAndIsActiveTrueOrderByNameAsc(UUID tenant);

    Optional<CustomTemperature> findFirstByTenantAndNameAndIsActiveTrue(UUID tenant, String name);
}
