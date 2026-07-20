package com.farmlog.auth;

import com.farmlog.auth.dto.AuthTokenResponse;
import com.farmlog.auth.dto.LoginRequest;
import com.farmlog.auth.dto.SignUpRequest;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.security.JwtAccessDeniedHandler;
import com.farmlog.common.security.JwtAuthenticationEntryPoint;
import com.farmlog.common.security.JwtAuthenticationFilter;
import com.farmlog.common.security.JwtTokenProvider;
import com.farmlog.common.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /auth/** 엔드포인트의 HTTP 계층(요청 검증, 에러 응답 포맷, 쿠키 발급) 테스트.
 * 실제 SecurityConfig/JwtAuthenticationFilter를 함께 로드해 필터 체인까지 검증하되, AuthService는
 * Mock으로 대체해 DB 접근 없이 컨트롤러/보안 설정만 검증한다.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtTokenProvider.class})
class AuthControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void signUp_success_returnsTokensAndSetsRefreshCookie() throws Exception {
        AuthTokenResponse response = new AuthTokenResponse(
                "access-token-value", "refresh-token-value", "Bearer", 1800, 1209600,
                new AuthTokenResponse.UserSummary(1L, "farmer@example.com", "홍길동"));
        when(authService.signUp(any())).thenReturn(response);

        SignUpRequest request = new SignUpRequest("farmer@example.com", "password123", "홍길동");

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-value"))
                .andExpect(jsonPath("$.user.email").value("farmer@example.com"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    void signUp_invalidEmail_returns400ValidationError() throws Exception {
        String invalidJson = """
                {"email": "not-an-email", "password": "password123", "displayName": "홍길동"}
                """;

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void signUp_shortPassword_returns400ValidationError() throws Exception {
        String invalidJson = """
                {"email": "farmer@example.com", "password": "short", "displayName": "홍길동"}
                """;

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void login_invalidCredentials_returns401WithCommonErrorFormat() throws Exception {
        when(authService.login(any())).thenThrow(new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        LoginRequest request = new LoginRequest("farmer@example.com", "wrong-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void refresh_usesCookieOverBody_whenBothPresent() throws Exception {
        AuthTokenResponse response = new AuthTokenResponse(
                "new-access-token", "new-refresh-token", "Bearer", 1800, 1209600,
                new AuthTokenResponse.UserSummary(1L, "farmer@example.com", "홍길동"));
        when(authService.refresh("cookie-refresh-token")).thenReturn(response);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"body-refresh-token\"}")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "cookie-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void refresh_noTokenAtAll_delegatesToServiceAndReturns401() throws Exception {
        when(authService.refresh(null)).thenThrow(new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void passwordResetRequest_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

}
