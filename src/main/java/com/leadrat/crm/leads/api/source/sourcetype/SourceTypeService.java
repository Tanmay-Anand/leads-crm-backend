package com.leadrat.crm.leads.api.source.sourcetype;

import com.leadrat.crm.leads.api.source.sourcetype.dto.SourceTypeRequest;

import java.util.List;
import java.util.UUID;

public interface SourceTypeService {

    /** All source types, or only those under parentId when it is supplied. */
    List<CustomSourceType> getAll(UUID parentId);

    CustomSourceType getById(UUID id);

    CustomSourceType add(SourceTypeRequest request);

    CustomSourceType update(UUID id, SourceTypeRequest request);

    void delete(UUID id);
}
