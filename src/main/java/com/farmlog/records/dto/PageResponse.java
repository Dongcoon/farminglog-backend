package com.farmlog.records.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int pages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(content, page, size, total, pages);
    }
}
