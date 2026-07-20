package com.farmlog.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Access/Refresh 토큰 발급 및 검증을 담당한다.
 *
 * <p>설계 원칙(plan/02_Development_Guide.md 4.4, 7.1):
 * <ul>
 *   <li>토큰에는 사용자의 전역 신원(userId, email)만 담는다. 농장별 역할은 담지 않는다 — 로그인 시점에는
 *       아직 어느 농장 컨텍스트인지 알 수 없고(농장 선택은 로그인 이후 화면 흐름), 역할은
 *       farm_member/organization_member를 통해 요청 시점에 조회하는 것이 최신 상태를 보장한다.</li>
 *   <li>Access 토큰과 Refresh 토큰은 {@code type} 클레임으로 구분해, Access 토큰을 Refresh 용도로
 *       재사용하는 것을 막는다.</li>
 *   <li>Refresh 토큰은 발급 시 매번 {@code jti}(고유 ID)를 부여해 동일 사용자가 여러 기기에서 로그인해도
 *       {@code refresh_token} 테이블에 별도 행으로 저장/폐기(rotate)할 수 있게 한다.</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_EMAIL = "email";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTokenExpirationMinutes;
    private final long refreshTokenExpirationDays;

    public JwtTokenProvider(
            @Value("${farmlog.jwt.secret}") String secret,
            @Value("${farmlog.jwt.access-token-expiration-minutes}") long accessTokenExpirationMinutes,
            @Value("${farmlog.jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    public String createAccessToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + Duration.ofMinutes(accessTokenExpirationMinutes).toMillis());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + Duration.ofDays(refreshTokenExpirationDays).toMillis());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public long getAccessTokenExpirationSeconds() {
        return Duration.ofMinutes(accessTokenExpirationMinutes).toSeconds();
    }

    public long getRefreshTokenExpirationSeconds() {
        return Duration.ofDays(refreshTokenExpirationDays).toSeconds();
    }

    public Date refreshTokenExpiryDate() {
        return new Date(System.currentTimeMillis() + Duration.ofDays(refreshTokenExpirationDays).toMillis());
    }

    /**
     * 서명/형식/만료를 검증하고 Claims를 반환한다. 만료된 토큰은 {@link ExpiredJwtException}을,
     * 그 외 위변조/형식 오류는 {@link JwtException} 또는 {@link IllegalArgumentException}을 던진다.
     * 호출부에서 구분해 AUTH_EXPIRED / AUTH_INVALID_TOKEN 에러 코드로 매핑한다.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getEmail(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }
}
