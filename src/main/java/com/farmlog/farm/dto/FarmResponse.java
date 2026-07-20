package com.farmlog.farm.dto;

/**
 * {@code GET /farms}, {@code POST /farms} 공통 응답. {@code role}은 로그인 사용자의 해당 농장
 * {@code farm_member.role}이며(Phase 1이 비워뒀던 프론트엔드 {@code currentFarmRole}을 채우는 필드),
 * {@code POST /farms}는 생성자 본인이므로 항상 {@code FARM_OWNER}가 내려간다.
 */
public record FarmResponse(
        Long id,
        String name,
        String farmCode,
        String address,
        String lifecycleStatus,
        String status,
        String role
) {
}
