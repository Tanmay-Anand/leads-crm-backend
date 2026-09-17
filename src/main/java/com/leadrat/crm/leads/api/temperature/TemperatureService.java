package com.leadrat.crm.leads.api.temperature;

import com.leadrat.crm.leads.api.temperature.dto.TemperatureRequest;

import java.util.List;
import java.util.UUID;

public interface TemperatureService {

    List<CustomTemperature> getAll();

    CustomTemperature getById(UUID id);

    CustomTemperature add(TemperatureRequest request);

    CustomTemperature update(UUID id, TemperatureRequest request);

    void delete(UUID id);
}
