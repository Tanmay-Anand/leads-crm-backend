package com.leadrat.crm.leads.api.channelpartner;

import com.leadrat.crm.leads.api.auth.annotations.AuthenticatedOnly;
import com.leadrat.crm.leads.api.core.EnumDto;
import com.leadrat.crm.leads.api.search.SearchResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/channel-partners")
@Tag(name = "Channel Partners", description = "Channel partner and broker management")
public class ChannelPartnerController {

    private final ChannelPartnerService channelPartnerService;

    @Operation(summary = "Get channel partners with pagination",
            description = "Supports free-text search via q (minimum 2 characters) and a date range via fromDate "
                    + "and toDate.")
    @GetMapping
    @PreAuthorize("@permissionService.check('view', 'channel-partners')")
    public ResponseEntity<Page<ChannelPartnerDto>> getAll(
            @Parameter(description = "Free-text search term, minimum 2 characters.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Comma-separated field names to search. Omit to search all fields.")
            @RequestParam(required = false) List<ChannelPartnerSearchField> searchFields,
            @Parameter(description = "Inclusive lower bound on created, as yyyy-MM-dd.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Inclusive upper bound on created, as yyyy-MM-dd.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable) {

        if (q != null && q.trim().length() >= 2) {
            return ResponseEntity.ok(
                    channelPartnerService.simpleSearch(q.trim(), searchFields, pageable, fromDate, toDate));
        }
        return ResponseEntity.ok(channelPartnerService.getAll(pageable, fromDate, toDate));
    }

    @Operation(summary = "Search channel partners with structured filters")
    @PostMapping("/search")
    @PreAuthorize("@permissionService.check('view', 'channel-partners')")
    public ResponseEntity<Page<ChannelPartnerDto>> search(
            @ParameterObject @PageableDefault(sort = "created", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestBody @Valid List<SearchResource> resources) {
        return ResponseEntity.ok(channelPartnerService.search(resources, pageable));
    }

    @Operation(summary = "Get all channel partners as id and name pairs",
            description = "Feeds the partner picker on the lead form and the partner filter dropdown.")
    @GetMapping("/names")
    @PreAuthorize("@permissionService.check('view', 'channel-partners')")
    public ResponseEntity<List<ChannelPartnerNamesDto>> getNames() {
        return ResponseEntity.ok(channelPartnerService.getNames());
    }

    @Operation(summary = "Get channel partner counts for the list header")
    @GetMapping("/stats")
    @PreAuthorize("@permissionService.check('view', 'channel-partners')")
    public ResponseEntity<ChannelPartnerStatsDto> getStats() {
        return ResponseEntity.ok(channelPartnerService.getStats());
    }

    @Operation(summary = "Get the enum options the channel partner form needs")
    @GetMapping("/enums")
    @AuthenticatedOnly
    public ResponseEntity<Map<String, List<EnumDto>>> getEnums() {
        return ResponseEntity.ok(Map.of(
                "partnerTypes", Arrays.stream(ChannelPartnerType.values()).map(EnumDto::of).toList(),
                "tiers", Arrays.stream(ChannelPartnerTier.values()).map(EnumDto::of).toList(),
                "onboardingStatuses",
                        Arrays.stream(ChannelPartnerOnboardingStatus.values()).map(EnumDto::of).toList(),
                "primaryMarkets", Arrays.stream(PrimaryMarket.values()).map(EnumDto::of).toList(),
                "accountTypes", Arrays.stream(BankAccountType.values()).map(EnumDto::of).toList(),
                "commissionTypes", Arrays.stream(CommissionType.values()).map(EnumDto::of).toList(),
                "commissionPayoutTriggers",
                        Arrays.stream(CommissionPayoutTrigger.values()).map(EnumDto::of).toList()));
    }

    @Operation(summary = "Get a channel partner by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Channel partner found"),
            @ApiResponse(responseCode = "404", description = "Channel partner not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.check('view', 'channel-partners')")
    public ResponseEntity<ChannelPartnerDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(channelPartnerService.get(id));
    }

    @Operation(summary = "Create a new channel partner")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Channel partner created"),
            @ApiResponse(responseCode = "409", description = "A partner with that email already exists")
    })
    @PostMapping
    @PreAuthorize("@permissionService.check('add', 'channel-partners')")
    public ResponseEntity<ChannelPartnerDto> save(@Valid @RequestBody ChannelPartnerDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(channelPartnerService.save(request));
    }

    @Operation(summary = "Update an existing channel partner")
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.check('update', 'channel-partners')")
    public ResponseEntity<ChannelPartnerDto> edit(@PathVariable UUID id,
                                                  @Valid @RequestBody ChannelPartnerDto request) {
        return ResponseEntity.ok(channelPartnerService.edit(id, request));
    }

    @Operation(summary = "Soft-delete a channel partner",
            description = "Refused with 409 while active leads are still attributed to the partner.")
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.check('delete', 'channel-partners')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        channelPartnerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
