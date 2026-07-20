package com.farmlog.common.security;

/**
 * 인증된 사용자의 전역(글로벌) 신원 정보.
 *
 * <p>주의: 이 객체는 사용자의 <b>전역</b> 신원(userId, email)만 담는다. 농장/조직별 역할(FARM_OWNER,
 * FARM_MANAGER 등)은 여기 포함하지 않는다 — 역할은 로그인 시점이 아니라 농장 컨텍스트가 선택된 요청별로
 * {@code common/tenant} 검증 로직(Phase 2 이후 구현)에서 {@code farm_member}/{@code organization_member}
 * 테이블을 조회해 판단한다. 자세한 근거는 plan/04_Open_Questions_Log.md의 Phase 1 신규 항목을 참고.</p>
 */
public record UserPrincipal(Long userId, String email) {
}
