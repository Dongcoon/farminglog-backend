package com.farmlog.farm.dto;

import java.time.LocalDate;

/**
 * {@code GET /farms/{farmId}/zone-assignments} 응답. Phase 8(농장 병합/분리)이 소비할 구역 소속
 * 이력 조회 API이며, 이번 Phase에서는 UI 없이 API만 제공한다.
 */
public record FarmZoneAssignmentResponse(
        Long id,
        Long zoneId,
        Long farmId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean activeYn
) {
}
