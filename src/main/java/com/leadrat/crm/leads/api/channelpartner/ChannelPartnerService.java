package com.leadrat.crm.leads.api.channelpartner;

import com.leadrat.crm.leads.api.search.SearchResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ChannelPartnerService {

    Page<ChannelPartnerDto> getAll(Pageable pageable, LocalDate fromDate, LocalDate toDate);

    Page<ChannelPartnerDto> simpleSearch(String query, List<ChannelPartnerSearchField> searchFields,
                                         Pageable pageable, LocalDate fromDate, LocalDate toDate);

    Page<ChannelPartnerDto> search(List<SearchResource> resources, Pageable pageable);

    /** Id and name pairs for partner pickers. */
    List<ChannelPartnerNamesDto> getNames();

    ChannelPartnerStatsDto getStats();

    ChannelPartnerDto get(UUID id);

    ChannelPartnerDto save(ChannelPartnerDto request);

    ChannelPartnerDto edit(UUID id, ChannelPartnerDto request);

    void delete(UUID id);
}
