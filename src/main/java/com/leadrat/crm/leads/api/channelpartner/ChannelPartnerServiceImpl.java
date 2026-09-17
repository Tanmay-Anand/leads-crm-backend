package com.leadrat.crm.leads.api.channelpartner;

import com.leadrat.crm.leads.api.exception.EntityNotFoundException;
import com.leadrat.crm.leads.api.exception.LeadratException;
import com.leadrat.crm.leads.api.lead.LeadRepository;
import com.leadrat.crm.leads.api.search.SearchResource;
import com.leadrat.crm.leads.api.search.TenantAwareSearchSpecificationBuilder;
import com.leadrat.crm.leads.api.tenant.TenantAware;
import com.leadrat.crm.leads.api.util.Validations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelPartnerServiceImpl implements ChannelPartnerService {

    private final ChannelPartnerRepository channelPartnerRepository;
    private final LeadRepository leadRepository;
    private final TenantAware tenantAware;

    @Override
    @Transactional(readOnly = true)
    public Page<ChannelPartnerDto> getAll(Pageable pageable, LocalDate fromDate, LocalDate toDate) {
        UUID tenantId = requireTenant();
        Specification<ChannelPartner> spec = dateRangeSpec(fromDate, toDate);
        Page<ChannelPartner> page = spec == null
                ? channelPartnerRepository.findByTenant(tenantId, pageable)
                : channelPartnerRepository.findAll(spec, pageable);
        return page.map(partner -> toDto(partner, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChannelPartnerDto> simpleSearch(String query, List<ChannelPartnerSearchField> searchFields,
                                                Pageable pageable, LocalDate fromDate, LocalDate toDate) {
        UUID tenantId = requireTenant();
        Specification<ChannelPartner> spec = new ChannelPartnerSimpleSearchSpecification(query, searchFields);

        Specification<ChannelPartner> dateSpec = dateRangeSpec(fromDate, toDate);
        if (dateSpec != null) {
            spec = spec.and(dateSpec);
        }

        return channelPartnerRepository.findAll(spec, pageable).map(partner -> toDto(partner, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChannelPartnerDto> search(List<SearchResource> resources, Pageable pageable) {
        UUID tenantId = requireTenant();
        Specification<ChannelPartner> spec =
                new TenantAwareSearchSpecificationBuilder<ChannelPartner>(resources, tenantId).build();
        return channelPartnerRepository.findAll(spec, pageable).map(partner -> toDto(partner, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelPartnerNamesDto> getNames() {
        return channelPartnerRepository.findByTenantAndIsActiveTrueOrderByNameAsc(requireTenant()).stream()
                .map(p -> new ChannelPartnerNamesDto(p.getId(), p.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelPartnerStatsDto getStats() {
        UUID tenantId = requireTenant();
        return new ChannelPartnerStatsDto(
                channelPartnerRepository.countByTenantAndIsActiveTrue(tenantId),
                channelPartnerRepository.countByTenantAndOnboardingStatusAndIsActiveTrue(
                        tenantId, ChannelPartnerOnboardingStatus.ACTIVE),
                channelPartnerRepository.countByTenantAndOnboardingStatusAndIsActiveTrue(
                        tenantId, ChannelPartnerOnboardingStatus.DRAFT),
                channelPartnerRepository.countByTenantAndTierAndIsActiveTrue(
                        tenantId, ChannelPartnerTier.PLATINUM));
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelPartnerDto get(UUID id) {
        return toDto(requirePartner(id), requireTenant());
    }

    @Override
    @Transactional
    public ChannelPartnerDto save(ChannelPartnerDto request) {
        UUID tenantId = requireTenant();
        validateFormats(request);

        channelPartnerRepository.findFirstByTenantAndEmailIgnoreCaseAndIsActiveTrue(tenantId, request.email())
                .ifPresent(existing -> {
                    throw new LeadratException("A channel partner with this email already exists.",
                            HttpStatus.CONFLICT);
                });

        ChannelPartner partner = request.toEntity();
        partner.tenant(tenantId);
        ChannelPartner saved = channelPartnerRepository.save(partner);
        log.info("Created channel partner {} for tenant {}", saved.getId(), tenantId);
        return toDto(saved, tenantId);
    }

    @Override
    @Transactional
    public ChannelPartnerDto edit(UUID id, ChannelPartnerDto request) {
        UUID tenantId = requireTenant();
        ChannelPartner partner = requirePartner(id);
        validateFormats(request);

        if (channelPartnerRepository.existsByTenantAndEmailIgnoreCaseAndIsActiveTrueAndIdNot(
                tenantId, request.email(), id)) {
            throw new LeadratException("A channel partner with this email already exists.", HttpStatus.CONFLICT);
        }

        request.applyTo(partner);
        return toDto(channelPartnerRepository.save(partner), tenantId);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = requireTenant();
        ChannelPartner partner = requirePartner(id);

        // A lead holds channelPartnerId with no FK, so nothing at the database level would stop
        // this. Attribution is the partner claim on that lead, so silently orphaning it would lose
        // the record of who sourced it.
        if (leadRepository.existsByTenantAndChannelPartnerIdAndIsActiveTrue(tenantId, id)) {
            long count = leadRepository.countByTenantAndChannelPartnerIdAndIsActiveTrue(tenantId, id);
            throw new LeadratException(
                    count + " active leads are attributed to this channel partner. Reassign them before deleting it.",
                    HttpStatus.CONFLICT);
        }

        channelPartnerRepository.delete(partner);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void validateFormats(ChannelPartnerDto request) {
        Validations.isValidEmail(request.email());
        Validations.isValidEmail(request.alternateEmail());
        Validations.isValidMobile(request.primaryPhone());
        Validations.isValidMobile(request.secondaryPhone());
        Validations.isValidPan(request.ownerPan());
        Validations.isValidIfsc(request.ifscCode());
        Validations.isValidGstIn(request.gstNumber());
        Validations.isValidRera(request.reraRegNumber());
    }

    private ChannelPartnerDto toDto(ChannelPartner partner, UUID tenantId) {
        return ChannelPartnerDto.fromEntity(partner,
                leadRepository.countByTenantAndChannelPartnerIdAndIsActiveTrue(tenantId, partner.getId()));
    }

    private ChannelPartner requirePartner(UUID id) {
        ChannelPartner partner = channelPartnerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Channel partner not found: " + id));
        tenantAware.validate(partner);
        return partner;
    }

    private Specification<ChannelPartner> dateRangeSpec(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return null;
        }
        return (root, cq, cb) -> {
            if (fromDate != null && toDate != null) {
                return cb.between(root.get("created"), fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX));
            }
            if (fromDate != null) {
                return cb.greaterThanOrEqualTo(root.get("created"), fromDate.atStartOfDay());
            }
            return cb.lessThanOrEqualTo(root.get("created"), toDate.atTime(LocalTime.MAX));
        };
    }

    private UUID requireTenant() {
        UUID tenantId = tenantAware.getTenantId();
        if (tenantId == null) {
            throw new LeadratException("Tenant context is required for this operation",
                    HttpStatus.PRECONDITION_FAILED);
        }
        return tenantId;
    }
}
