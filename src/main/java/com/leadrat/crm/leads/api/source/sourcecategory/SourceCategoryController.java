package com.leadrat.crm.leads.api.source.sourcecategory;

import com.leadrat.crm.leads.api.source.sourcecategory.dto.SourceCategoryDto;
import com.leadrat.crm.leads.api.source.sourcecategory.dto.SourceCategoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/source-categories")
@Tag(name = "Source Category", description = "Manage the top level of the lead source taxonomy")
public class SourceCategoryController {

    private final SourceCategoryService sourceCategoryService;

    @Operation(summary = "Get all source categories for the current tenant")
    @GetMapping
    @PreAuthorize("@permissionService.check('view', 'master-data/sources')")
    public ResponseEntity<List<SourceCategoryDto>> getAll() {
        return ResponseEntity.ok(sourceCategoryService.getAll().stream().map(SourceCategoryDto::from).toList());
    }

    @Operation(summary = "Get a source category by ID")
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.check('view', 'master-data/sources')")
    public ResponseEntity<SourceCategoryDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(SourceCategoryDto.from(sourceCategoryService.getById(id)));
    }

    @Operation(summary = "Create a new source category")
    @PostMapping
    @PreAuthorize("@permissionService.check('add', 'master-data/sources')")
    public ResponseEntity<SourceCategoryDto> add(@Valid @RequestBody SourceCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SourceCategoryDto.from(sourceCategoryService.add(request)));
    }

    @Operation(summary = "Update a source category")
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.check('update', 'master-data/sources')")
    public ResponseEntity<SourceCategoryDto> update(@PathVariable UUID id,
                                                    @Valid @RequestBody SourceCategoryRequest request) {
        return ResponseEntity.ok(SourceCategoryDto.from(sourceCategoryService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a source category")
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.check('delete', 'master-data/sources')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sourceCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
