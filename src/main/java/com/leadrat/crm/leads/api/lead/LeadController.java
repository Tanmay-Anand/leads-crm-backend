package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.auth.annotations.AuthenticatedOnly;
import com.leadrat.crm.leads.api.core.EnumDto;
import com.leadrat.crm.leads.api.lead.dto.CreateLeadRequest;
import com.leadrat.crm.leads.api.lead.dto.DuplicateCheckResponse;
import com.leadrat.crm.leads.api.lead.dto.LeadAssignmentUpdateRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadDto;
import com.leadrat.crm.leads.api.lead.dto.LeadSourceUpdateRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadStatusUpdateRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadSummaryDto;
import com.leadrat.crm.leads.api.lead.dto.LeadTagsUpdateRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadTemperatureUpdateRequest;
import com.leadrat.crm.leads.api.search.SearchResource;
import com.leadrat.crm.leads.api.search.advanced.AdvancedSearchRequest;
import com.leadrat.crm.leads.api.search.advanced.FilterFieldDto;
import com.leadrat.crm.leads.api.search.advanced.FilterOptionDto;
import com.leadrat.crm.leads.api.search.advanced.UnknownFieldPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/leads")
@Tag(name = "Lead", description = "Manage leads")
public class LeadController {

    private final LeadService leadService;

    @Operation(summary = "Get all leads",
            description = "Returns a paginated list of leads. Supply q (minimum 2 characters) to activate free-text "
                    + "search across all fields, or searchFields to restrict it to specific ones. The date range "
                    + "fromDate and toDate composes with both the plain list and the text search. "
                    + "Searchable fields: NAME, LEAD_CODE, MOBILE, EMAIL, ASSIGNED_TO, CHANNEL_PARTNER, TELECALLER, "
                    + "PROPERTY_CATEGORY, STATUS, TEMPERATURE, TAG, CITY.")
    @GetMapping
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<Page<LeadDto>> getAll(
            @Parameter(description = "Free-text search term, minimum 2 characters.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Comma-separated field names to search. Omit to search all fields.")
            @RequestParam(required = false) List<LeadSearchField> searchFields,
            @Parameter(description = "Inclusive lower bound, as yyyy-MM-dd. Works with and without q.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Inclusive upper bound, as yyyy-MM-dd. Works with and without q.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Which date field the range applies to: created (default), modified or scheduleDate.")
            @RequestParam(required = false, defaultValue = "created") String dateType,
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable) {

        if (q != null && q.trim().length() >= 2) {
            return ResponseEntity.ok(
                    leadService.simpleSearch(q.trim(), searchFields, pageable, fromDate, toDate, dateType));
        }
        return ResponseEntity.ok(leadService.getAll(pageable, fromDate, toDate, dateType));
    }

    @Operation(summary = "Get available filter fields for advanced search",
            description = "Returns the metadata (key, label, group, value type, allowed operators) for every field "
                    + "usable in POST /leads/advanced-search. The filter drawer is generated from this.")
    @GetMapping("/filter-fields")
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<List<FilterFieldDto>> getFilterFields() {
        return ResponseEntity.ok(leadService.getFilterFields());
    }

    @Operation(summary = "Get selectable options for a filter field",
            description = "Returns the value and label pairs the UI should show in the filter dropdown for the given "
                    + "optionsSource, as declared on each FilterFieldDto. Fetched lazily, only when the user opens "
                    + "that dropdown.")
    @GetMapping("/filter-options/{optionsSource}")
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<List<FilterOptionDto>> getFilterOptions(@PathVariable String optionsSource) {
        return ResponseEntity.ok(leadService.getFilterOptions(optionsSource));
    }

    @Operation(summary = "Advanced search for leads",
            description = "Structured filter search. Pass criteria as an array of field, operator and values objects, "
                    + "where field is a key returned by GET /leads/filter-fields. Pagination and sorting travel as "
                    + "query parameters. scope filters by assignment: ALL (default), MINE or UNASSIGNED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "400", description = "Unknown field key, or an operator that field disallows")
    })
    @PostMapping("/advanced-search")
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<Page<LeadDto>> advancedSearch(
            @RequestBody @Valid AdvancedSearchRequest request,
            @Parameter(description = "What to do with a criterion the registry cannot serve. REJECT (default) returns "
                    + "400; SKIP ignores it and searches on the rest. Send SKIP when replaying a saved filter, so one "
                    + "removed field cannot break the page.")
            @RequestParam(required = false, defaultValue = "REJECT") UnknownFieldPolicy onUnknownField,
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(leadService.advancedSearch(request, pageable, onUnknownField.isReject()));
    }

    @Operation(summary = "Search leads with untyped field filters")
    @PostMapping("/search")
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<Page<LeadDto>> search(
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestBody @Valid List<SearchResource> resources) {
        return ResponseEntity.ok(leadService.search(resources, pageable));
    }

    @Operation(summary = "Get lead counts for the list header")
    @GetMapping("/summary")
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<LeadSummaryDto> getSummary() {
        return ResponseEntity.ok(leadService.getSummary());
    }

    @Operation(summary = "Get the enum options the lead form needs")
    @GetMapping("/enums")
    @AuthenticatedOnly
    public ResponseEntity<Map<String, List<EnumDto>>> getEnums() {
        return ResponseEntity.ok(Map.of(
                "propertyCategories", Arrays.stream(PropertyCategory.values()).map(EnumDto::of).toList(),
                "purchaseTimelines", Arrays.stream(PurchaseTimeline.values()).map(EnumDto::of).toList(),
                "assignmentMethods", Arrays.stream(AssignmentMethod.values()).map(EnumDto::of).toList(),
                "noteTypes", Arrays.stream(LeadNoteType.values()).map(EnumDto::of).toList()));
    }

    @Operation(summary = "Check whether a mobile number already exists",
            description = "projectId matters: a lead is unique per tenant, project and mobile, so the same person may "
                    + "exist on several projects. Omit it to check the project-less bucket.")
    @GetMapping("/check-mobile")
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<DuplicateCheckResponse> checkMobile(
            @Parameter(description = "Mobile number to check", example = "9876543210", required = true)
            @RequestParam String mobile,
            @Parameter(description = "Dialling code, such as +91. Defaults to India when omitted.", example = "+91")
            @RequestParam(required = false) String countryCode,
            @Parameter(description = "Project to check within.")
            @RequestParam(required = false) UUID projectId) {
        return ResponseEntity.ok(leadService.checkMobile(mobile, countryCode, projectId));
    }

    @Operation(summary = "Get a lead by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lead found"),
            @ApiResponse(responseCode = "404", description = "Lead not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.check('view', 'leads')")
    public ResponseEntity<LeadDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(leadService.getById(id));
    }

    @Operation(summary = "Create a new lead")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lead created"),
            @ApiResponse(responseCode = "409", description = "A lead with that mobile already exists on the project")
    })
    @PostMapping
    @PreAuthorize("@permissionService.check('add', 'leads')")
    public ResponseEntity<LeadDto> add(@Valid @RequestBody CreateLeadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.add(request));
    }

    @Operation(summary = "Replace a lead")
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadDto> update(@PathVariable UUID id, @Valid @RequestBody CreateLeadRequest request) {
        return ResponseEntity.ok(leadService.update(id, request));
    }

    @Operation(summary = "Move a lead to a status",
            description = "A note is required when the target status is configured with isNoteRequired; it is "
                    + "recorded against the lead as well as driving the transition.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadDto> updateStatus(@PathVariable UUID id,
                                                @Valid @RequestBody LeadStatusUpdateRequest request) {
        return ResponseEntity.ok(leadService.updateStatus(id, request));
    }

    @Operation(summary = "Replace the tags on a lead")
    @PatchMapping("/{id}/tags")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadDto> updateTags(@PathVariable UUID id,
                                              @Valid @RequestBody LeadTagsUpdateRequest request) {
        return ResponseEntity.ok(leadService.updateTags(id, request));
    }

    @Operation(summary = "Set the temperature on a lead")
    @PatchMapping("/{id}/temperature")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadDto> updateTemperature(@PathVariable UUID id,
                                                     @Valid @RequestBody LeadTemperatureUpdateRequest request) {
        return ResponseEntity.ok(leadService.updateTemperature(id, request));
    }

    @Operation(summary = "Set the source on a lead")
    @PatchMapping("/{id}/source")
    @PreAuthorize("@permissionService.check('update', 'leads')")
    public ResponseEntity<LeadDto> updateSource(@PathVariable UUID id,
                                                @Valid @RequestBody LeadSourceUpdateRequest request) {
        return ResponseEntity.ok(leadService.updateSource(id, request));
    }

    @Operation(summary = "Reassign a lead")
    @PatchMapping("/{id}/assignment")
    @PreAuthorize("@permissionService.check('assign', 'leads')")
    public ResponseEntity<LeadDto> updateAssignment(@PathVariable UUID id,
                                                    @Valid @RequestBody LeadAssignmentUpdateRequest request) {
        return ResponseEntity.ok(leadService.updateAssignment(id, request));
    }

    @Operation(summary = "Soft-delete a lead")
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.check('delete', 'leads')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        leadService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
