package com.farmlog.records.dto;

import java.time.LocalDate;

public record RecordFilter(Long farmId, LocalDate dateFrom, LocalDate dateTo, Long zoneId, Long cropId, Long varietyId,
                           Long seasonId, Long createdBy, Long workTypeId, Long customerId,
                           String settlementStatus, int page, int size, String orderBy, String direction) {
    public int offset() { return page * size; }
}
