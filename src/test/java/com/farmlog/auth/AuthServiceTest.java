package com.farmlog.auth;

import com.farmlog.auth.dto.AuthTokenResponse;
import com.farmlog.auth.dto.LoginRequest;
import com.farmlog.auth.dto.MessageResponse;
import com.farmlog.auth.dto.PasswordResetConfirmRequest;
import com.farmlog.auth.dto.PasswordResetRequestRequest;
import com.farmlog.auth.dto.SignUpRequest;
import com.farmlog.auth.entity.OrganizationEntity;
import com.farmlog.auth.entity.OrganizationMemberEntity;
import com.farmlog.auth.entity.PasswordResetTokenEntity;
import com.farmlog.auth.entity.RefreshTokenEntity;
import com.farmlog.auth.mapper.AuthMapper;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.security.JwtTokenProvider;
import com.farmlog.user.entity.UserEntity;
import com.farmlog.user.mapper.UserMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 단위 테스트. 실제 DB 없이 UserMapper/AuthMapper를 Mockito로 대체한다
 * (plan/02_Development_Guide.md 10장 "자동화 테스트 수준: 핵심 E2E 시나리오만" + Phase 1 지시사항에 따라,
 * MyBatis Mapper XML 자체의 SQL 검증은 이번 범위에서 별도의 실 DB/H2 통합 테스트로 다루지 않고
 * 서비스 계층 로직 검증에 집중한다. 근거는 plan/04_Open_Questions_Log.md Phase 1 신규 항목 참고).
 * JwtTokenProvider는 실제 구현을 그대로 사용해 토큰 발급/검증 로직까지 함께 검증한다.
 */
class AuthServiceTest {

    private static final String TEST_SECRET = "test-only-farmlog-jwt-secret-key-must-be-long-enough-for-hs256";

    private UserMapper userMapper;
    private AuthMapper authMapper;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        authMapper = mock(AuthMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, 30, 14);
        authService = new AuthService(userMapper, authMapper, passwordEncoder, jwtTokenProvider,
                "http://localhost:5173/password-reset");

        AtomicLong userIdSeq = new AtomicLong(1);
        AtomicLong otherIdSeq = new AtomicLong(1);

        when(userMapper.findByEmail(any())).thenReturn(Optional.empty());

        org.mockito.stubbing.Answer<Void> assignUserId = invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(userIdSeq.getAndIncrement());
            return null;
        };
        org.mockito.Mockito.doAnswer(assignUserId).when(userMapper).insert(any(UserEntity.class));

        org.mockito.stubbing.Answer<Void> assignOrgId = invocation -> {
            OrganizationEntity entity = invocation.getArgument(0);
            entity.setId(otherIdSeq.getAndIncrement());
            return null;
        };
        org.mockito.Mockito.doAnswer(assignOrgId).when(authMapper).insertOrganization(any(OrganizationEntity.class));

        org.mockito.stubbing.Answer<Void> assignMemberId = invocation -> {
            OrganizationMemberEntity entity = invocation.getArgument(0);
            entity.setId(otherIdSeq.getAndIncrement());
            return null;
        };
        org.mockito.Mockito.doAnswer(assignMemberId).when(authMapper).insertOrganizationMember(any(OrganizationMemberEntity.class));

        org.mockito.stubbing.Answer<Void> assignRefreshTokenId = invocation -> {
            RefreshTokenEntity entity = invocation.getArgument(0);
            entity.setId(otherIdSeq.getAndIncrement());
            return null;
        };
        org.mockito.Mockito.doAnswer(assignRefreshTokenId).when(authMapper).insertRefreshToken(any(RefreshTokenEntity.class));

        org.mockito.stubbing.Answer<Void> assignResetTokenId = invocation -> {
            PasswordResetTokenEntity entity = invocation.getArgument(0);
            entity.setId(otherIdSeq.getAndIncrement());
            return null;
        };
        org.mockito.Mockito.doAnswer(assignResetTokenId).when(authMapper).insertPasswordResetToken(any(PasswordResetTokenEntity.class));
    }

    @Test
    void signUp_success_createsUserPersonalOrgAndTokens() {
        SignUpRequest request = new SignUpRequest("Farmer@Example.com", "password123", "홍길동");

        AuthTokenResponse response = authService.signUp(request);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("farmer@example.com");
        assertThat(response.user().displayName()).isEqualTo("홍길동");

        verify(userMapper, times(1)).insert(any(UserEntity.class));
        verify(authMapper, times(1)).insertOrganization(any(OrganizationEntity.class));
        verify(authMapper, times(1)).insertOrganizationMember(any(OrganizationMemberEntity.class));
        verify(authMapper, times(1)).insertRefreshToken(any(RefreshTokenEntity.class));
    }

    @Test
    void signUp_duplicateEmail_throwsBusinessException() {
        UserEntity existing = UserEntity.builder().id(1L).email("dup@example.com").status("ACTIVE").build();
        when(userMapper.findByEmail("dup@example.com")).thenReturn(Optional.of(existing));

        SignUpRequest request = new SignUpRequest("dup@example.com", "password123", "홍길동");

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATE));

        verify(userMapper, never()).insert(any());
    }

    @Test
    void login_success_returnsTokensAndUpdatesLastLogin() {
        UserEntity user = activeUser(1L, "farmer@example.com", "password123!");
        when(userMapper.findByEmail("farmer@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("farmer@example.com", "password123!");

        AuthTokenResponse response = authService.login(request);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(1L);
        verify(userMapper, times(1)).updateLastLoginAt(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        UserEntity user = activeUser(1L, "farmer@example.com", "password123!");
        when(userMapper.findByEmail("farmer@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("farmer@example.com", "wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials_notUserNotFound() {
        when(userMapper.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("nobody@example.com", "password123!");

        // 이메일 존재 여부를 노출하지 않기 위해 미가입 계정도 AUTH_INVALID_CREDENTIALS로 통일한다.
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    @Test
    void refresh_success_rotatesRefreshToken() {
        UserEntity user = activeUser(1L, "farmer@example.com", "password123!");
        when(userMapper.findById(1L)).thenReturn(Optional.of(user));

        String rawRefreshToken = jwtTokenProvider.createRefreshToken(1L, "farmer@example.com");
        String tokenHash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshTokenEntity stored = RefreshTokenEntity.builder()
                .id(99L)
                .userId(1L)
                .tokenHash(tokenHash)
                .issuedAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(LocalDateTime.now().plusDays(13))
                .build();
        when(authMapper.findRefreshTokenByHash(tokenHash)).thenReturn(Optional.of(stored));

        AuthTokenResponse response = authService.refresh(rawRefreshToken);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo(rawRefreshToken);
        verify(authMapper, times(1)).revokeRefreshToken(eq(99L), any(LocalDateTime.class), anyLong());
        verify(authMapper, times(1)).insertRefreshToken(any(RefreshTokenEntity.class));
    }

    @Test
    void refresh_revokedToken_throwsInvalidToken() {
        String rawRefreshToken = jwtTokenProvider.createRefreshToken(1L, "farmer@example.com");
        String tokenHash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshTokenEntity stored = RefreshTokenEntity.builder()
                .id(99L)
                .userId(1L)
                .tokenHash(tokenHash)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(13))
                .revokedAt(LocalDateTime.now().minusHours(1))
                .build();
        when(authMapper.findRefreshTokenByHash(tokenHash)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(rawRefreshToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN));
    }

    @Test
    void refresh_unknownToken_throwsInvalidToken() {
        String rawRefreshToken = jwtTokenProvider.createRefreshToken(1L, "farmer@example.com");
        when(authMapper.findRefreshTokenByHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(rawRefreshToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN));
    }

    @Test
    void refresh_expiredJwt_throwsAuthExpired() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("email", "farmer@example.com")
                .claim("type", "refresh")
                .issuedAt(new Date(System.currentTimeMillis() - 100_000))
                .expiration(new Date(System.currentTimeMillis() - 50_000))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> authService.refresh(expiredToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_EXPIRED));
    }

    @Test
    void refresh_missingToken_throwsInvalidToken() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN));
    }

    @Test
    void refresh_accessTokenRejected_throwsInvalidToken() {
        // Access 토큰을 Refresh 용도로 재사용하려는 시도는 거부되어야 한다.
        String accessToken = jwtTokenProvider.createAccessToken(1L, "farmer@example.com");

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN));
    }

    @Test
    void requestPasswordReset_unknownEmail_stillReturnsGenericSuccess() {
        when(userMapper.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        MessageResponse response = authService.requestPasswordReset(new PasswordResetRequestRequest("nobody@example.com"));

        assertThat(response.message()).isNotBlank();
        verify(authMapper, never()).insertPasswordResetToken(any());
    }

    @Test
    void requestPasswordReset_knownEmail_createsToken() {
        UserEntity user = activeUser(1L, "farmer@example.com", "password123!");
        when(userMapper.findByEmail("farmer@example.com")).thenReturn(Optional.of(user));

        MessageResponse response = authService.requestPasswordReset(new PasswordResetRequestRequest("farmer@example.com"));

        assertThat(response.message()).isNotBlank();
        verify(authMapper, times(1)).insertPasswordResetToken(any(PasswordResetTokenEntity.class));
    }

    @Test
    void confirmPasswordReset_invalidToken_throwsBusinessException() {
        when(authMapper.findPasswordResetTokenByHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmPasswordReset(new PasswordResetConfirmRequest("bad-token", "newpassword1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN));
    }

    @Test
    void confirmPasswordReset_expiredToken_throwsBusinessException() {
        String rawToken = "reset-token";
        PasswordResetTokenEntity entity = PasswordResetTokenEntity.builder()
                .id(1L)
                .userId(1L)
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(31))
                .build();
        when(authMapper.findPasswordResetTokenByHash(TokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> authService.confirmPasswordReset(new PasswordResetConfirmRequest(rawToken, "newpassword1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_TOKEN));
    }

    @Test
    void confirmPasswordReset_success_updatesPasswordAndRevokesRefreshTokens() {
        String rawToken = "reset-token";
        PasswordResetTokenEntity entity = PasswordResetTokenEntity.builder()
                .id(1L)
                .userId(1L)
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(29))
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(authMapper.findPasswordResetTokenByHash(TokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(entity));
        when(userMapper.findById(1L)).thenReturn(Optional.of(activeUser(1L, "farmer@example.com", "oldpassword")));

        MessageResponse response = authService.confirmPasswordReset(new PasswordResetConfirmRequest(rawToken, "newpassword1"));

        assertThat(response.message()).isNotBlank();
        verify(userMapper, times(1)).updatePasswordHash(eq(1L), any(), any());
        verify(authMapper, times(1)).markPasswordResetTokenUsed(eq(1L), any());
        verify(authMapper, times(1)).revokeAllRefreshTokensForUser(eq(1L), any());
    }

    private UserEntity activeUser(Long id, String email, String rawPassword) {
        return UserEntity.builder()
                .id(id)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName("테스트 사용자")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }
}
