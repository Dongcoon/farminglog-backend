package com.farmlog.common.tenant;

/**
 * 특정 {@code farmId}에 대한 인증된 사용자의 ACTIVE {@code farm_member} 조회 결과.
 * {@link FarmAccessGuard#requireFarmMember(Long, Long)}가 반환한다.
 */
public record FarmMembership(Long farmId, Long userId, String role) {
}
