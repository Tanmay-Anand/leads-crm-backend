package com.leadrat.crm.leads.api.tag;

import com.leadrat.crm.leads.api.tag.dto.TagRequest;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TagService {

    List<CustomTag> getAll();

    CustomTag getById(UUID id);

    /** Resolves a set of tag ids in one query, for the lead read model. */
    List<CustomTag> getByIds(Collection<UUID> ids);

    CustomTag add(TagRequest request);

    CustomTag update(UUID id, TagRequest request);

    void delete(UUID id);
}
