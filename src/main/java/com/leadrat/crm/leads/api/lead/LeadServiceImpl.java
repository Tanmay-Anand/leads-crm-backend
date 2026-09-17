package com.leadrat.crm.leads.api.lead;

import com.leadrat.crm.leads.api.channelpartner.ChannelPartnerRepository;
import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.lead.dto.CreateLeadRequest;
import com.leadrat.crm.leads.api.lead.dto.DuplicateCheckResponse;
import com.leadrat.crm.leads.api.lead.dto.LeadAssignmentUpdateRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadDto;
import com.leadrat.crm.leads.api.lead.dto.LeadNoteRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadSourceUpdateRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadStatusUpdateRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadSummaryDto;
import com.leadrat.crm.leads.api.lead.dto.LeadTagsUpdateRequest;
import com.leadrat.crm.leads.api.lead.dto.LeadTemperatureUpdateRequest;
import com.leadrat.crm.leads.api.leadstatus.LeadStatus;
import com.leadrat.crm.leads.api.leadstatus.LeadStatusRepository;
import com.leadrat.crm.leads.api.leadstatus.dto.LeadStatusDto;
import com.leadrat.crm.leads.api.project.ProjectRepository;
import com.leadrat.crm.leads.api.search.SearchResource;
import com.leadrat.crm.leads.api.search.TenantAwareSearchSpecificationBuilder;
import com.leadrat.crm.leads.api.search.advanced.AdvancedSearchRequest;
import com.leadrat.crm.leads.api.search.advanced.AdvancedSearchSpecification;
import com.leadrat.crm.leads.api.search.advanced.FilterFieldDto;
import com.leadrat.crm.leads.api.search.advanced.FilterFieldRegistry;
import com.leadrat.crm.leads.api.search.advanced.FilterOptionDto;
import com.leadrat.crm.leads.api.search.advanced.FilterOptions;
import com.leadrat.crm.leads.api.seeding.TenantSeedingService;
import com.leadrat.crm.leads.api.source.sourcecategory.CustomSourceCategory;
import com.leadrat.crm.leads.api.source.sourcecategory.CustomSourceCategoryRepository;
import com.leadrat.crm.leads.api.source.sourcecategory.dto.SourceCategoryDto;
import com.leadrat.crm.leads.api.source.sourcetype.CustomSourceType;
import com.leadrat.crm.leads.api.source.sourcetype.CustomSourceTypeRepository;
import com.leadrat.crm.leads.api.source.sourcetype.dto.SourceTypeDto;
import com.leadrat.crm.leads.api.tag.CustomTag;
import com.leadrat.crm.leads.api.tag.CustomTagRepository;
import com.leadrat.crm.leads.api.tag.dto.TagDto;
import com.leadrat.crm.leads.api.temperature.CustomTemperature;
import com.leadrat.crm.leads.api.temperature.CustomTemperatureRepository;
import com.leadrat.crm.leads.api.temperature.dto.TemperatureDto;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadServiceImpl implements LeadService {

    private final LeadRepository leadRepository;
    private final LeadSequenceService leadSequenceService;
    private final LeadNoteService leadNoteService;
    private final LeadStatusRepository leadStatusRepository;
    private final CustomTemperatureRepository temperatureRepository;
    private final CustomTagRepository tagRepository;
    private final CustomSourceCategoryRepository sourceCategoryRepository;
    private final CustomSourceTypeRepository sourceTypeRepository;
    private final ProjectRepository projectRepository;
    private final ChannelPartnerRepository channelPartnerRepository;
    private final FilterFieldRegistry<Lead, LeadFilterField> leadFilterFieldRegistry;
    private final TenantSeedingService tenantSeedingService;
    private final TenantAware tenantAware;

    // ─── Reads ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<LeadDto> getAll(Pageable pageable, LocalDate fromDate, LocalDate toDate, String dateType) {
        UUID tenantId = requireTenant();
        tenantSeedingService.ensureSeeded(tenantId);

        Specification<Lead> dateSpec = dateRangeSpec(fromDate, toDate, dateType);
        Page<Lead> page = dateSpec == null
                ? leadRepository.findByTenant(tenantId, pageable)
                : leadRepository.findAll(dateSpec, pageable);

        return toDtoPage(page, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeadDto> simpleSearch(String query, List<LeadSearchField> searchFields, Pageable pageable,
                                      LocalDate fromDate, LocalDate toDate, String dateType) {
        UUID tenantId = requireTenant();

        // Status, temperature and tag are stored on the lead as ids, so a text search has to be
        // resolved to ids first. Doing it here keeps the specification a single query over leads.
        Set<UUID> statusIds = matchingStatusIds(tenantId, query);
        Set<UUID> temperatureIds = matchingTemperatureIds(tenantId, query);
        Set<UUID> tagIds = matchingTagIds(tenantId, query);

        Specification<Lead> spec =
                new LeadSimpleSearchSpecification(query, searchFields, statusIds, temperatureIds, tagIds);

        Specification<Lead> dateSpec = dateRangeSpec(fromDate, toDate, dateType);
        if (dateSpec != null) {
            spec = spec.and(dateSpec);
        }

        return toDtoPage(leadRepository.findAll(spec, pageable), tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeadDto> advancedSearch(AdvancedSearchRequest request, Pageable pageable, boolean rejectUnknown) {
        UUID tenantId = requireTenant();

        Specification<Lead> spec =
                AdvancedSearchSpecification.build(request, leadFilterFieldRegistry, tenantId, rejectUnknown);

        Specification<Lead> scopeSpec = scopeSpec(request.scope());
        if (scopeSpec != null) {
            spec = spec.and(scopeSpec);
        }

        // The free-text box stays usable while the drawer is open, so q composes with the criteria
        // rather than replacing them.
        if (request.q() != null && request.q().trim().length() >= 2) {
            String q = request.q().trim();
            List<LeadSearchField> qFields = parseSearchFields(request.qFields());
            spec = spec.and(new LeadSimpleSearchSpecification(q, qFields,
                    matchingStatusIds(tenantId, q),
                    matchingTemperatureIds(tenantId, q),
                    matchingTagIds(tenantId, q)));
        }

        Specification<Lead> dateSpec = dateRangeSpec(request.fromDate(), request.toDate(), request.dateType());
        if (dateSpec != null) {
            spec = spec.and(dateSpec);
        }

        return toDtoPage(leadRepository.findAll(spec, pageable), tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeadDto> search(List<SearchResource> resources, Pageable pageable) {
        UUID tenantId = requireTenant();
        Specification<Lead> spec = new TenantAwareSearchSpecificationBuilder<Lead>(resources, tenantId).build();
        return toDtoPage(leadRepository.findAll(spec, pageable), tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FilterFieldDto> getFilterFields() {
        return leadFilterFieldRegistry.fieldDtos();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FilterOptionDto> getFilterOptions(String optionsSource) {
        UUID tenantId = requireTenant();

        return switch (optionsSource) {
            case "leadStatuses" -> leadStatusRepository.findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(tenantId)
                    .stream().map(s -> new FilterOptionDto(s.getId().toString(), label(s.getDisplayName(), s.getName())))
                    .toList();

            case "temperatures" -> temperatureRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId)
                    .stream().map(t -> new FilterOptionDto(t.getId().toString(), label(t.getDisplayName(), t.getName())))
                    .toList();

            case "tags" -> tagRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId)
                    .stream().map(t -> new FilterOptionDto(t.getId().toString(), label(t.getDisplayName(), t.getName())))
                    .toList();

            case "sourceCategories" -> sourceCategoryRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId)
                    .stream().map(c -> new FilterOptionDto(c.getId().toString(), label(c.getDisplayName(), c.getName())))
                    .toList();

            case "sourceTypes" -> sourceTypeRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId)
                    .stream().map(t -> new FilterOptionDto(t.getId().toString(), label(t.getDisplayName(), t.getName())))
                    .toList();

            case "projects" -> projectRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId)
                    .stream().map(p -> new FilterOptionDto(p.getId().toString(), p.getName()))
                    .toList();

            case "channelPartners" -> channelPartnerRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId)
                    .stream().map(p -> new FilterOptionDto(p.getId().toString(), p.getName()))
                    .toList();

            // There is no user table in this service, so the owner dropdown is built from the
            // names already denormalised onto leads. A user who has never held a lead is not a
            // useful filter value anyway.
            case "users" -> leadRepository.findDistinctAssignees(tenantId).stream()
                    .map(row -> new FilterOptionDto(String.valueOf(row[0]), String.valueOf(row[1])))
                    .toList();

            case "assignmentMethods" -> FilterOptions.ofEnum(AssignmentMethod.values());
            case "propertyCategories" -> FilterOptions.ofEnum(PropertyCategory.values());
            case "purchaseTimelines" -> FilterOptions.ofEnum(PurchaseTimeline.values());

            default -> throw new LeadratException("Unknown filter options source: " + optionsSource,
                    HttpStatus.BAD_REQUEST);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public LeadDto getById(UUID id) {
        UUID tenantId = requireTenant();
        return toDto(requireLead(id), new LookupCache(tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public LeadSummaryDto getSummary() {
        UUID tenantId = requireTenant();
        tenantSeedingService.ensureSeeded(tenantId);

        List<Lead> leads = leadRepository.findByTenantAndIsActiveTrue(tenantId);
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        long createdThisMonth = leads.stream()
                .filter(l -> l.getCreated() != null && !l.getCreated().isBefore(monthStart))
                .count();
        long unassigned = leads.stream().filter(l -> l.getAssignedTo() == null).count();
        long dueToday = leads.stream()
                .filter(l -> l.getScheduleDate() != null && l.getScheduleDate().toLocalDate().isEqual(today))
                .count();

        Map<UUID, Long> countsByStatus = leads.stream()
                .filter(l -> l.getStatusId() != null)
                .collect(Collectors.groupingBy(Lead::getStatusId, Collectors.counting()));

        List<LeadSummaryDto.StatusCount> byStatus =
                leadStatusRepository.findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(tenantId).stream()
                        .map(status -> new LeadSummaryDto.StatusCount(
                                status.getId().toString(),
                                label(status.getDisplayName(), status.getName()),
                                status.getColorCode(),
                                countsByStatus.getOrDefault(status.getId(), 0L)))
                        .toList();

        return new LeadSummaryDto(leads.size(), createdThisMonth, unassigned, dueToday, byStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public DuplicateCheckResponse checkMobile(String mobile, String countryCode, UUID projectId) {
        UUID tenantId = requireTenant();
        return findByIdentity(tenantId, mobile, countryCode, projectId)
                .map(lead -> DuplicateCheckResponse.found(toDto(lead, new LookupCache(tenantId))))
                .orElseGet(DuplicateCheckResponse::notFound);
    }

    // ─── Writes ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public LeadDto add(CreateLeadRequest request) {
        UUID tenantId = requireTenant();
        UUID userId = currentUserId();
        request.validate();
        tenantSeedingService.ensureSeeded(tenantId);

        // Checked up front so the common case reports a business conflict rather than a constraint
        // violation. The unique index still backs it for the request that loses a race, and
        // HttpExceptionHandler maps that to the same 409 with the same wording.
        findByIdentity(tenantId, request.mobile(), request.countryCode(), request.projectId())
                .ifPresent(existing -> {
                    throw new LeadratException(
                            "A lead with this mobile number already exists for this project.", HttpStatus.CONFLICT);
                });

        Lead lead = new Lead();
        lead.tenant(tenantId);
        lead.setLeadCode(leadSequenceService.nextCode(tenantId));
        applyRequest(lead, request, tenantId);
        lead.setCreatedByUserId(userId);
        lead.setLastModifiedByUserId(userId);

        // A lead with no status cannot be grouped or filtered, so fall back to the tenant default.
        if (lead.getStatusId() == null) {
            leadStatusRepository.findFirstByTenantAndIsDefaultTrueAndIsActiveTrue(tenantId)
                    .ifPresent(status -> lead.setStatusId(status.getId()));
        }
        if (lead.getAssignmentMethod() == null) {
            lead.setAssignmentMethod(lead.getAssignedTo() != null
                    ? AssignmentMethod.MANUAL
                    : AssignmentMethod.POOL);
        }

        Lead saved = leadRepository.save(lead);
        log.info("Created lead {} ({}) for tenant {}", saved.getId(), saved.getLeadCode(), tenantId);
        return toDto(saved, new LookupCache(tenantId));
    }

    @Override
    @Transactional
    public LeadDto update(UUID id, CreateLeadRequest request) {
        UUID tenantId = requireTenant();
        Lead lead = requireLead(id);
        request.validate();

        // The identity key can move when mobile or project changes, so re-check it — but only
        // against other leads, or a plain save would report the lead as its own duplicate.
        findByIdentity(tenantId, request.mobile(), request.countryCode(), request.projectId())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new LeadratException(
                            "A lead with this mobile number already exists for this project.", HttpStatus.CONFLICT);
                });

        applyRequest(lead, request, tenantId);
        lead.setLastModifiedByUserId(currentUserId());

        return toDto(leadRepository.save(lead), new LookupCache(tenantId));
    }

    @Override
    @Transactional
    public LeadDto updateStatus(UUID id, LeadStatusUpdateRequest request) {
        UUID tenantId = requireTenant();
        Lead lead = requireLead(id);

        LeadStatus status = leadStatusRepository.findById(request.statusId())
                .orElseThrow(() -> new EntityNotFoundException("Lead status not found: " + request.statusId()));
        tenantAware.validate(status);

        // Enforced here rather than as a DTO constraint because whether a note is required depends
        // on the target status row, which the annotation cannot see.
        if (status.isNoteRequired() && (request.note() == null || request.note().isBlank())) {
            throw new LeadratException(
                    "A note is required to move a lead to " + label(status.getDisplayName(), status.getName()) + ".",
                    HttpStatus.PRECONDITION_FAILED);
        }

        lead.setStatusId(status.getId());
        lead.setLastModifiedByUserId(currentUserId());
        Lead saved = leadRepository.save(lead);

        if (request.note() != null && !request.note().isBlank()) {
            leadNoteService.add(id, new LeadNoteRequest(LeadNoteType.NOTE, request.note(), null));
        }

        return toDto(saved, new LookupCache(tenantId));
    }

    @Override
    @Transactional
    public LeadDto updateTags(UUID id, LeadTagsUpdateRequest request) {
        UUID tenantId = requireTenant();
        Lead lead = requireLead(id);

        Set<String> resolved = resolveTagIds(tenantId, request.tagIds());
        lead.setTagIds(new HashSet<>(resolved));
        lead.setLastModifiedByUserId(currentUserId());

        return toDto(leadRepository.save(lead), new LookupCache(tenantId));
    }

    @Override
    @Transactional
    public LeadDto updateTemperature(UUID id, LeadTemperatureUpdateRequest request) {
        UUID tenantId = requireTenant();
        Lead lead = requireLead(id);

        CustomTemperature temperature = temperatureRepository.findById(request.temperatureId())
                .orElseThrow(() -> new EntityNotFoundException("Temperature not found: " + request.temperatureId()));
        tenantAware.validate(temperature);

        lead.setTemperatureId(temperature.getId());
        lead.setLastModifiedByUserId(currentUserId());

        return toDto(leadRepository.save(lead), new LookupCache(tenantId));
    }

    @Override
    @Transactional
    public LeadDto updateSource(UUID id, LeadSourceUpdateRequest request) {
        UUID tenantId = requireTenant();
        Lead lead = requireLead(id);

        lead.setSourceCategoryId(requireSourceCategory(request.sourceCategoryId()));
        lead.setSourceTypeId(requireSourceType(request.sourceTypeId()));
        lead.setLastModifiedByUserId(currentUserId());

        return toDto(leadRepository.save(lead), new LookupCache(tenantId));
    }

    @Override
    @Transactional
    public LeadDto updateAssignment(UUID id, LeadAssignmentUpdateRequest request) {
        UUID tenantId = requireTenant();
        Lead lead = requireLead(id);

        lead.setAssignedTo(request.assignedTo());
        lead.setAssignedToUserName(request.assignedToUserName());
        lead.setTelecallerId(request.telecallerId());
        lead.setTelecallerName(request.telecallerName());
        lead.setAssignmentMethod(request.assignmentMethod() != null
                ? request.assignmentMethod()
                : (request.assignedTo() != null ? AssignmentMethod.MANUAL : AssignmentMethod.POOL));
        lead.setLastModifiedByUserId(currentUserId());

        return toDto(leadRepository.save(lead), new LookupCache(tenantId));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Lead lead = requireLead(id);
        lead.setDeletedOn(LocalDateTime.now());
        lead.setDeletedByUserId(currentUserId());
        leadRepository.save(lead);
        leadRepository.delete(lead);
    }

    // ─── Write helpers ────────────────────────────────────────────────────────

    /** Copies the request onto the entity, validating every id it references belongs to the tenant. */
    private void applyRequest(Lead lead, CreateLeadRequest request, UUID tenantId) {
        lead.setFirstName(request.firstName());
        lead.setLastName(request.lastName());
        lead.setMobile(request.mobile());
        lead.setCountryCode(request.countryCode());
        lead.setMobileNormalized(LeadIdentity.normalize(request.mobile(), request.countryCode()));
        lead.setAlternateMobile(request.alternateMobile());
        lead.setAlternateCountryCode(request.alternateCountryCode());
        lead.setEmail(request.email());
        lead.setOccupation(request.occupation());
        lead.setAddress(request.address());
        lead.setPropertyCategory(request.propertyCategory());
        lead.setPurchaseTimeline(request.purchaseTimeline());
        lead.setNotes(request.notes());
        lead.setNri(request.isNri());
        lead.setDraft(request.isDraft());
        lead.setScheduleDate(request.scheduleDate());

        lead.setProjectId(requireProject(request.projectId()));
        lead.setChannelPartnerId(resolveChannelPartner(lead, request.channelPartnerId()));
        lead.setTelecallerId(request.telecallerId());
        lead.setTelecallerName(request.telecallerName());
        lead.setAssignedTo(request.assignedTo());
        lead.setAssignedToUserName(request.assignedToUserName());

        if (request.assignmentMethod() != null) {
            lead.setAssignmentMethod(request.assignmentMethod());
        }
        if (request.statusId() != null) {
            lead.setStatusId(requireStatus(request.statusId()));
        }
        if (request.temperatureId() != null) {
            lead.setTemperatureId(requireTemperature(request.temperatureId()));
        }

        lead.setSourceCategoryId(requireSourceCategory(request.sourceCategoryId()));
        lead.setSourceTypeId(requireSourceType(request.sourceTypeId()));
        lead.setTagIds(new HashSet<>(resolveTagIds(tenantId, request.tagIds())));
    }

    /**
     * Resolves the partner and denormalises its name onto the lead.
     *
     * <p>The name is stored rather than joined at read time: it is shown on every list row and is
     * one of the searchable scopes, so keeping it on the lead avoids a join per page.
     */
    private UUID resolveChannelPartner(Lead lead, UUID channelPartnerId) {
        if (channelPartnerId == null) {
            lead.setChannelPartnerName(null);
            return null;
        }
        var partner = channelPartnerRepository.findById(channelPartnerId)
                .orElseThrow(() -> new EntityNotFoundException("Channel partner not found: " + channelPartnerId));
        tenantAware.validate(partner);
        lead.setChannelPartnerName(partner.getName());
        return partner.getId();
    }

    private UUID requireProject(UUID projectId) {
        if (projectId == null) {
            return null;
        }
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));
        tenantAware.validate(project);
        return project.getId();
    }

    private UUID requireStatus(UUID statusId) {
        if (statusId == null) {
            return null;
        }
        LeadStatus status = leadStatusRepository.findById(statusId)
                .orElseThrow(() -> new EntityNotFoundException("Lead status not found: " + statusId));
        tenantAware.validate(status);
        return status.getId();
    }

    private UUID requireTemperature(UUID temperatureId) {
        if (temperatureId == null) {
            return null;
        }
        CustomTemperature temperature = temperatureRepository.findById(temperatureId)
                .orElseThrow(() -> new EntityNotFoundException("Temperature not found: " + temperatureId));
        tenantAware.validate(temperature);
        return temperature.getId();
    }

    private UUID requireSourceCategory(UUID sourceCategoryId) {
        if (sourceCategoryId == null) {
            return null;
        }
        CustomSourceCategory category = sourceCategoryRepository.findById(sourceCategoryId)
                .orElseThrow(() -> new EntityNotFoundException("Source category not found: " + sourceCategoryId));
        tenantAware.validate(category);
        return category.getId();
    }

    private UUID requireSourceType(UUID sourceTypeId) {
        if (sourceTypeId == null) {
            return null;
        }
        CustomSourceType type = sourceTypeRepository.findById(sourceTypeId)
                .orElseThrow(() -> new EntityNotFoundException("Source type not found: " + sourceTypeId));
        tenantAware.validate(type);
        return type.getId();
    }

    /**
     * Keeps only the ids that resolve to a live tag in this tenant.
     *
     * <p>Silently dropping an unknown id rather than failing: tags are a soft classification and a
     * stale id in a bulk edit should not cost the user the rest of their change.
     */
    private Set<String> resolveTagIds(UUID tenantId, List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        return tagRepository.findByTenantAndIdInAndIsActiveTrue(tenantId, tagIds).stream()
                .map(tag -> tag.getId().toString())
                .collect(Collectors.toSet());
    }

    // ─── Read helpers ─────────────────────────────────────────────────────────

    /**
     * Resolves a mobile to the lead that owns that identity, within the given project.
     *
     * <p>Two finders rather than one, because SQL compares NULL with IS NULL: a project-less lead
     * would never match an equality on project_id.
     */
    private Optional<Lead> findByIdentity(UUID tenantId, String mobile, String countryCode, UUID projectId) {
        String normalized = LeadIdentity.normalize(mobile, countryCode);
        if (normalized == null) {
            return Optional.empty();
        }
        return projectId == null
                ? leadRepository.findFirstByTenantAndProjectIdIsNullAndMobileNormalizedAndIsActiveTrue(
                        tenantId, normalized)
                : leadRepository.findFirstByTenantAndProjectIdAndMobileNormalizedAndIsActiveTrue(
                        tenantId, projectId, normalized);
    }

    private Lead requireLead(UUID id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lead not found: " + id));
        tenantAware.validate(lead);
        return lead;
    }

    private Page<LeadDto> toDtoPage(Page<Lead> page, UUID tenantId) {
        LookupCache cache = new LookupCache(tenantId);
        return page.map(lead -> toDto(lead, cache));
    }

    private LeadDto toDto(Lead lead, LookupCache cache) {
        return LeadDto.from(
                lead,
                cache.status(lead.getStatusId()),
                cache.temperature(lead.getTemperatureId()),
                cache.sourceCategory(lead.getSourceCategoryId()),
                cache.sourceType(lead.getSourceTypeId()),
                cache.tags(lead.getTagIds()));
    }

    /**
     * Master data loaded once per page rather than once per row.
     *
     * <p>A lead carries five references to tenant master data, so mapping a 50-row page naively
     * would be 250 queries. Each collection is small and tenant-scoped, so loading all of them
     * lazily on first use costs at most five queries per page.
     */
    private final class LookupCache {

        private final UUID tenantId;
        private Map<UUID, LeadStatusDto> statuses;
        private Map<UUID, TemperatureDto> temperatures;
        private Map<UUID, SourceCategoryDto> sourceCategories;
        private Map<UUID, SourceTypeDto> sourceTypes;
        private Map<UUID, TagDto> tags;

        private LookupCache(UUID tenantId) {
            this.tenantId = tenantId;
        }

        private LeadStatusDto status(UUID id) {
            if (id == null) {
                return null;
            }
            if (statuses == null) {
                statuses = index(leadStatusRepository.findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(tenantId),
                        LeadStatus::getId, LeadStatusDto::from);
            }
            return statuses.get(id);
        }

        private TemperatureDto temperature(UUID id) {
            if (id == null) {
                return null;
            }
            if (temperatures == null) {
                temperatures = index(temperatureRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId),
                        CustomTemperature::getId, TemperatureDto::from);
            }
            return temperatures.get(id);
        }

        private SourceCategoryDto sourceCategory(UUID id) {
            if (id == null) {
                return null;
            }
            if (sourceCategories == null) {
                sourceCategories = index(sourceCategoryRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId),
                        CustomSourceCategory::getId, SourceCategoryDto::from);
            }
            return sourceCategories.get(id);
        }

        private SourceTypeDto sourceType(UUID id) {
            if (id == null) {
                return null;
            }
            if (sourceTypes == null) {
                sourceTypes = index(sourceTypeRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId),
                        CustomSourceType::getId, SourceTypeDto::from);
            }
            return sourceTypes.get(id);
        }

        private List<TagDto> tags(Set<String> ids) {
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            if (tags == null) {
                tags = index(tagRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId),
                        CustomTag::getId, TagDto::from);
            }
            List<TagDto> result = new ArrayList<>();
            for (String id : ids) {
                TagDto dto = tags.get(toUuid(id));
                if (dto != null) {
                    result.add(dto);
                }
            }
            return result;
        }

        private <E, D> Map<UUID, D> index(List<E> entities, Function<E, UUID> idOf, Function<E, D> toDto) {
            return entities.stream().collect(Collectors.toMap(idOf, toDto));
        }

        private UUID toUuid(String raw) {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    // ─── Specification helpers ────────────────────────────────────────────────

    /**
     * Inclusive range over one of the lead date columns.
     *
     * <p>dateType names which column, defaulting to created. An unknown value falls back to created
     * rather than failing: the parameter comes from a dropdown, and a stale saved filter should
     * narrow oddly rather than break the page.
     */
    private Specification<Lead> dateRangeSpec(LocalDate fromDate, LocalDate toDate, String dateType) {
        if (fromDate == null && toDate == null) {
            return null;
        }
        String column = switch (dateType == null ? "created" : dateType) {
            case "modified" -> "modified";
            case "scheduleDate" -> "scheduleDate";
            default -> "created";
        };

        return (root, cq, cb) -> {
            if (fromDate != null && toDate != null) {
                return cb.between(root.get(column), fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX));
            }
            if (fromDate != null) {
                return cb.greaterThanOrEqualTo(root.get(column), fromDate.atStartOfDay());
            }
            return cb.lessThanOrEqualTo(root.get(column), toDate.atTime(LocalTime.MAX));
        };
    }

    /** MINE and UNASSIGNED narrow by assignment; ALL and anything unrecognised do not narrow. */
    private Specification<Lead> scopeSpec(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        LeadScope parsed;
        try {
            parsed = LeadScope.valueOf(scope);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return switch (parsed) {
            case MINE -> {
                UUID userId = tenantAware.getLoggedInUserId();
                yield userId == null ? null : (root, cq, cb) -> cb.equal(root.get("assignedTo"), userId);
            }
            case UNASSIGNED -> (root, cq, cb) -> cb.isNull(root.get("assignedTo"));
            case ALL -> null;
        };
    }

    private List<LeadSearchField> parseSearchFields(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<LeadSearchField> fields = new ArrayList<>();
        for (String value : raw) {
            try {
                fields.add(LeadSearchField.valueOf(value));
            } catch (IllegalArgumentException e) {
                log.debug("Ignoring unknown lead search field: {}", value);
            }
        }
        return fields;
    }

    private Set<UUID> matchingStatusIds(UUID tenantId, String query) {
        return matchingIds(leadStatusRepository.findByTenantAndIsActiveTrueOrderByDisplayOrderAsc(tenantId),
                query, s -> label(s.getDisplayName(), s.getName()), LeadStatus::getId);
    }

    private Set<UUID> matchingTemperatureIds(UUID tenantId, String query) {
        return matchingIds(temperatureRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId),
                query, t -> label(t.getDisplayName(), t.getName()), CustomTemperature::getId);
    }

    private Set<UUID> matchingTagIds(UUID tenantId, String query) {
        return matchingIds(tagRepository.findByTenantAndIsActiveTrueOrderByNameAsc(tenantId),
                query, t -> label(t.getDisplayName(), t.getName()), CustomTag::getId);
    }

    private <E> Set<UUID> matchingIds(Collection<E> entities, String query,
                                      Function<E, String> labelOf, Function<E, UUID> idOf) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        String term = query.toLowerCase();
        return entities.stream()
                .filter(e -> labelOf.apply(e) != null && labelOf.apply(e).toLowerCase().contains(term))
                .map(idOf)
                .collect(Collectors.toSet());
    }

    private String label(String displayName, String name) {
        return displayName != null && !displayName.isBlank() ? displayName : name;
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
