package com.repairshop.saas.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** {content, page, size, totalElements, totalPages, counts} */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private Map<String, Long> counts;
}
