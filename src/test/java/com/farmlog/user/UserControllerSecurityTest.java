package com.farmlog.user;

import com.farmlog.common.security.JwtAccessDeniedHandler;
import com.farmlog.common.security.JwtAuthenticationEntryPoint;
import com.farmlog.common.security.JwtAuthenticationFilter;
import com.farmlog.common.security.JwtTokenProvider;
import com.farmlog.common.security.SecurityConfig;
import com.farmlog.user.dto.UserProfileResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보호된 API({@code /users/me})에 대한 인증 필터 체인 동작 검증.
 * plan/02_Development_Guide.md 9장 Phase 1 DoD: "다른 사용자 토큰으로 타 계정 리소스 접근 시 401/403"의
 * 전제가 되는 "토큰이 없거나/만료되었거나/위조된 경우 401" 케이스를 다룬다.
 */
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtTokenProvider.class})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${farmlog.jwt.secret}")
    private String jwtSecret;

    @MockBean
    private UserService userService;

    @Test
    void protectedApi_preflightFromLocalWebOrigin_returnsCorsHeadersWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/users/me/access-context")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void getMyProfile_withoutAuthorizationHeader_returns401InvalidToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void getMyProfile_withGarbageToken_returns401InvalidToken() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void getMyProfile_withExpiredToken_returns401AuthExpired() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expiredAccessToken = Jwts.builder()
                .subject("1")
                .claim("email", "farmer@example.com")
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 100_000))
                .expiration(new Date(System.currentTimeMillis() - 50_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + expiredAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_EXPIRED"));
    }

    @Test
    void getMyProfile_withRefreshTokenInsteadOfAccessToken_returns401InvalidToken() throws Exception {
        // Refresh Token으로는 보호된 API에 접근할 수 없어야 한다(type 클레임으로 access/refresh 구분).
        String refreshToken = jwtTokenProvider.createRefreshToken(1L, "farmer@example.com");

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void getMyProfile_withValidAccessToken_returns200AndResolvesPrincipalUserId() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(42L, "farmer@example.com");
        UserProfileResponse profile = new UserProfileResponse(42L, "farmer@example.com", "홍길동", "ACTIVE",
                null, LocalDateTime.now());
        when(userService.getMyProfile(eq(42L))).thenReturn(profile);

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("farmer@example.com"));
    }
}
