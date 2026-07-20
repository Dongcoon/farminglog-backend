package com.farmlog.auth.dto;

/**
 * 로그인/가입/토큰 갱신 공통 응답.
 *
 * <p>설계 근거(plan/02_Development_Guide.md 4.4, README 인증 절 참고): PC 웹은 Refresh Token을
 * httpOnly 쿠키로만 사용하고 본문의 {@code refreshToken} 값은 무시하면 되지만, 모바일(Expo SecureStore
 * 저장)은 쿠키를 사용할 수 없으므로 본문에도 Refresh Token을 함께 내려준다. 즉 서버는 항상 (1) 응답
 * 본문에 refreshToken을 포함하고, (2) 동시에 Set-Cookie로 httpOnly 쿠키를 내려준다 — 클라이언트가
 * 플랫폼에 맞는 쪽을 선택해서 사용하는 구조다.</p>
 */
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        long refreshExpiresInSeconds,
        UserSummary user
) {

    public record UserSummary(Long id, String email, String displayName) {
    }
}
