package com.leadrat.crm.leads.api.temperature;

import com.leadrat.crm.leads.api.temperature.dto.TemperatureDto;
import com.leadrat.crm.leads.api.temperature.dto.TemperatureRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/temperatures")
@Tag(name = "Temperature", description = "Manage tenant lead temperatures")
public class TemperatureController {

    private final TemperatureService temperatureService;

    @Operation(summary = "Get all temperatures for the current tenant")
    @GetMapping
    public ResponseEntity<List<TemperatureDto>> getAll() {
        return ResponseEntity.ok(temperatureService.getAll().stream().map(TemperatureDto::from).toList());
    }

    @Operation(summary = "Get a temperature by ID")
    @GetMapping("/{id}")
    public ResponseEntity<TemperatureDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(TemperatureDto.from(temperatureService.getById(id)));
    }

    @Operation(summary = "Create a new temperature")
    @PostMapping
    public ResponseEntity<TemperatureDto> add(@Valid @RequestBody TemperatureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(TemperatureDto.from(temperatureService.add(request)));
    }

    @Operation(summary = "Update a temperature")
    @PutMapping("/{id}")
    public ResponseEntity<TemperatureDto> update(@PathVariable UUID id,
                                                 @Valid @RequestBody TemperatureRequest request) {
        return ResponseEntity.ok(TemperatureDto.from(temperatureService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a temperature")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        temperatureService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
