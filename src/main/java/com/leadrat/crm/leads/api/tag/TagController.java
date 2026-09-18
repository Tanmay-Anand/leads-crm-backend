package com.leadrat.crm.leads.api.tag;

import com.leadrat.crm.leads.api.tag.dto.TagDto;
import com.leadrat.crm.leads.api.tag.dto.TagRequest;
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
@RequestMapping("/tags")
@Tag(name = "Tag", description = "Manage tenant lead tags")
public class TagController {

    private final TagService tagService;

    @Operation(summary = "Get all tags for the current tenant")
    @GetMapping
    @PreAuthorize("@permissionService.check('view', 'master-data/tags')")
    public ResponseEntity<List<TagDto>> getAll() {
        return ResponseEntity.ok(tagService.getAll().stream().map(TagDto::from).toList());
    }

    @Operation(summary = "Get a tag by ID")
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.check('view', 'master-data/tags')")
    public ResponseEntity<TagDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(TagDto.from(tagService.getById(id)));
    }

    @Operation(summary = "Create a new tag")
    @PostMapping
    @PreAuthorize("@permissionService.check('add', 'master-data/tags')")
    public ResponseEntity<TagDto> add(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(TagDto.from(tagService.add(request)));
    }

    @Operation(summary = "Update a tag")
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.check('update', 'master-data/tags')")
    public ResponseEntity<TagDto> update(@PathVariable UUID id, @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(TagDto.from(tagService.update(id, request)));
    }

    @Operation(summary = "Soft-delete a tag")
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.check('delete', 'master-data/tags')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
