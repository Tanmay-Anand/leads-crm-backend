package com.leadrat.crm.leads.api.leadstatus;

import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusDto;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusReorderRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusUpdateRequest;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusUsageDto;
import com.leadrat.crm.leads.api.search.SearchResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/leads/statuses")
@Tag(name = "Lead Status", description = "Manage tenant lead statuses (pipeline stages)")
public class LeadStatusController {

    private final LeadStatusService leadStatusService;

    @Operation(summary = "Get all lead statuses for the current tenant")
    @GetMapping
    @PreAuthorize("@permissionService.check('view', 'master-data/lead-statuses')")
    public ResponseEntity<Page<LeadStatusDto>> getAll(
            @ParameterObject @PageableDefault(sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(leadStatusService.getAll(pageable).map(LeadStatusDto::from));
    }

    @Operation(summary = "Search lead statuses with filters")
    @PostMapping("/search")
    @PreAuthorize("@permissionService.check('view', 'master-data/lead-statuses')")
    public ResponseEntity<Page<LeadStatusDto>> search(
            @ParameterObject @PageableDefault(sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestBody @Valid List<SearchResource> resources) {
        return ResponseEntity.ok(leadStatusService.search(resources, pageable).map(LeadStatusDto::from));
    }

    @Operation(summary = "Get all lead statuses as an ordered list")
    @GetMapping("/list")
    @PreAuthorize("@permissionService.check('view', 'master-data/lead-statuses')")
    public ResponseEntity<List<LeadStatusDto>> getList() {
        return ResponseEntity.ok(leadStatusService.getNames().stream().map(LeadStatusDto::from).toList());
    }

    @Operation(summary = "Get a lead status by ID")
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.check('view', 'master-data/lead-statuses')")
    public ResponseEntity<LeadStatusDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(LeadStatusDto.from(leadStatusService.getById(id)));
    }

    @Operation(summary = "Create a new lead status")
    @PostMapping
    @PreAuthorize("@permissionService.check('add', 'master-data/lead-statuses')")
    public ResponseEntity<LeadStatusDto> add(@Valid @RequestBody LeadStatusRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(LeadStatusDto.from(leadStatusService.add(request)));
    }

    @Operation(summary = "Update a lead status (partial, null fields are ignored)")
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.check('update', 'master-data/lead-statuses')")
    public ResponseEntity<LeadStatusDto> update(@PathVariable UUID id,
                                                @Valid @RequestBody LeadStatusUpdateRequest request) {
        return ResponseEntity.ok(LeadStatusDto.from(leadStatusService.update(id, request)));
    }

    @Operation(summary = "Reorder lead statuses by supplying an ordered list of IDs")
    @PostMapping("/order")
    @PreAuthorize("@permissionService.check('update', 'master-data/lead-statuses')")
    public ResponseEntity<List<LeadStatusDto>> reorder(@Valid @RequestBody LeadStatusReorderRequest request) {
        return ResponseEntity.ok(leadStatusService.reorder(request).stream().map(LeadStatusDto::from).toList());
    }

    @Operation(summary = "Number of active leads sitting on a lead status")
    @GetMapping("/{id}/usage")
    @PreAuthorize("@permissionService.check('view', 'master-data/lead-statuses')")
    public ResponseEntity<LeadStatusUsageDto> usage(@PathVariable UUID id) {
        return ResponseEntity.ok(leadStatusService.usage(id));
    }

    @Operation(summary = "Soft-delete a lead status",
            description = "When active leads sit on this status, targetStatusId must be supplied and the leads "
                    + "are moved there.")
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.check('delete', 'master-data/lead-statuses')")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestParam(required = false) UUID targetStatusId) {
        leadStatusService.delete(id, targetStatusId);
        return ResponseEntity.noContent().build();
    }
}
