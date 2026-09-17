package com.leadrat.crm.leads.api.core.response;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
public class PaginatedResponse<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
    private boolean first;
    private boolean last;

    public PaginatedResponse() {
    }

    public PaginatedResponse(Page<T> page) {
        this.content = page.getContent();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
        this.first = page.isFirst();
        this.last = page.isLast();
    }

    public static <T> PaginatedResponse<T> of(Page<T> page) {
        return new PaginatedResponse<>(page);
    }
}
