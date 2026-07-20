package com.farmlog.auth;

import com.farmlog.auth.dto.AuthTokenResponse;
import com.farmlog.auth.dto.LoginRequest;
import com.farmlog.auth.dto.MessageResponse;
import com.farmlog.auth.dto.PasswordResetConfirmRequest;
import com.farmlog.auth.dto.PasswordResetRequestRequest;
import com.farmlog.auth.dto.RefreshRequest;
import com.farmlog.auth.dto.SignUpRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입/로그인/토큰 갱신/비밀번호 재설정 API. 모두 SecurityConfig의 PUBLIC_PATHS(/auth/**)에 포함되어
 * 인증 없이 접근 가능하다.
 *
 * <p>Refresh Token 전달 방식(4.4 기반 설계 결정, plan/04_Open_Questions_Log.md 참고): 서버는
 * 로그인/가입/갱신 응답마다 (1) 응답 본문에 refreshToken을 포함하고 (2) 동시에 httpOnly 쿠키로도
 * 내려준다. PC 웹은 쿠키를 신뢰하고 본문 값은 무시하면 되며, 모바일은 쿠키를 사용할 수 없으므로 본문의
 * refreshToken을 SecureStore에 저장한다. {@code POST /auth/refresh}는 쿠키가 있으면 쿠키를 우선
 * 사용하고, 없으면 요청 본문의 {@code refreshToken} 필드를 사용한다.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    /** 로컬 http 전용 범위(plan 0-1절 "도메인/HTTPS: 없음, 로컬 http만") — secure 플래그는 사용하지 않는다. */
    private static final boolean COOKIE_SECURE = false;

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthTokenResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        AuthTokenResponse response = authService.signUp(request);
        return withRefreshCookie(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenResponse response = authService.login(request);
        return withRefreshCookie(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String cookieToken) {
        String rawRefreshToken = StringUtils.hasText(cookieToken)
                ? cookieToken
                : (request != null ? request.refreshToken() : null);

        AuthTokenResponse response = authService.refresh(rawRefreshToken);
        return withRefreshCookie(response);
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        return ResponseEntity.ok(authService.requestPasswordReset(request));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<MessageResponse> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return ResponseEntity.ok(authService.confirmPasswordReset(request));
    }

    private ResponseEntity<AuthTokenResponse> withRefreshCookie(AuthTokenResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, response.refreshToken())
                .httpOnly(true)
                .secure(COOKIE_SECURE)
                .sameSite("Lax")
                .path("/api/v1")
                .maxAge(response.refreshExpiresInSeconds() > 0 ? response.refreshExpiresInSeconds() : 0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }
}
