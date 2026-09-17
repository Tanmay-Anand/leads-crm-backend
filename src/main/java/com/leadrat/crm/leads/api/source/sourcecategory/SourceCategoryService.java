package com.leadrat.crm.leads.api.source.sourcecategory;

import com.leadrat.crm.leads.api.source.sourcecategory.dto.SourceCategoryRequest;

import java.util.List;
import java.util.UUID;

public interface SourceCategoryService {

    List<CustomSourceCategory> getAll();

    CustomSourceCategory getById(UUID id);

    CustomSourceCategory add(SourceCategoryRequest request);

    CustomSourceCategory update(UUID id, SourceCategoryRequest request);

    void delete(UUID id);
}
