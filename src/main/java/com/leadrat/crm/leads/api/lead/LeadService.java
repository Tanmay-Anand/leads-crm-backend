package com.leadrat.crm.leads.api.lead;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeadService {

    Page<LeadDto> getAll(Pageable pageable, LocalDate fromDate, LocalDate toDate, String dateType);

    Page<LeadDto> simpleSearch(String query, List<LeadSearchField> searchFields, Pageable pageable,
                               LocalDate fromDate, LocalDate toDate, String dateType);

    Page<LeadDto> advancedSearch(AdvancedSearchRequest request, Pageable pageable, boolean rejectUnknown);

    Page<LeadDto> search(List<SearchResource> resources, Pageable pageable);

    /** Metadata driving the filter drawer. */
    List<FilterFieldDto> getFilterFields();

    /** Options behind one filter dropdown, fetched lazily when the user opens it. */
    List<FilterOptionDto> getFilterOptions(String optionsSource);

    LeadDto getById(UUID id);

    LeadSummaryDto getSummary();

    LeadDto add(CreateLeadRequest request);

    LeadDto update(UUID id, CreateLeadRequest request);

    LeadDto updateStatus(UUID id, LeadStatusUpdateRequest request);

    LeadDto updateTags(UUID id, LeadTagsUpdateRequest request);

    LeadDto updateTemperature(UUID id, LeadTemperatureUpdateRequest request);

    LeadDto updateSource(UUID id, LeadSourceUpdateRequest request);

    LeadDto updateAssignment(UUID id, LeadAssignmentUpdateRequest request);

    void delete(UUID id);

    /**
     * Whether a lead already exists for this mobile, and when it does, who holds it.
     *
     * <p>projectId matters: a lead is unique per (tenant, project, mobile), so the same person may
     * legitimately exist on several projects. A null project asks about the project-less bucket,
     * which is a real answer rather than any project.
     *
     * <p>Resolved through the same lookup the write path uses, so the check and the create can
     * never disagree.
     */
    DuplicateCheckResponse checkMobile(String mobile, String countryCode, UUID projectId);
}
