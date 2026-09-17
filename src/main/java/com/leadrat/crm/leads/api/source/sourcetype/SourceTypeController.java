package com.leadrat.crm.leads.api.source.sourcetype;

import com.leadrat.crm.leads.api.source.sourcetype.dto.SourceTypeDto;
import com.leadrat.crm.leads.api.source.sourcetype.dto.SourceTypeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/source-types")
@Tag(name = "Source Type", description = "Manage the second level of the lead source taxonomy")
public class SourceTypeController {

    private final SourceTypeService sourceTypeService;

    @Operation(summary = "Get all source types for the current tenant")
    @GetMapping
    public ResponseEntity<List<SourceTypeDto>> getAll(
            @Parameter(description = "Restrict to the source types under this category.")
            @RequestParam(required = false) UUID parentId) {
        return ResponseEntity.ok(sourceTypeService.getAll(parentId).stream().map(SourceTypeDto::from).toList());
    }

    @Operation(summary = "Get a source type by ID")
    @GetMapping("/{id}")
    public ResponseEntity<SourceTypeDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(SourceTypeDto.from(sourceTypeService.getById(id)));
    }

    @Operation(summary = "Create a new source type")
    @PostMapping
    public ResponseEntity<SourceTypeDto> add(@Valid @RequestBody SourceTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SourceTypeDto.from(sourceTypeService.add(request)));
    }

    @Operation(summary = "Update a source type")
    @PutMapping("/{id}")
    public ResponseEntity<SourceTypeDto> update(@PathVariable UUID id,
                                                @Valid @RequestBody SourceTypeRequest request) {
        return ResponseEntity.ok(SourceTypeDto.from(sourceTypeService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a source type")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sourceTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
