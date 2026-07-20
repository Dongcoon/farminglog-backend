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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 인증(가입/로그인/토큰 갱신/비밀번호 재설정) 비즈니스 로직.
 *
 * <p>역할 설계 원칙(plan/02_Development_Guide.md 4.4, 7.1): JWT에는 사용자 전역 신원(userId, email)만
 * 담고, 농장별 역할(FARM_OWNER 등)은 담지 않는다. 농장 역할 판단은 Phase 2 이후 {@code common/tenant}
 * 검증 로직이 {@code farm_member}를 조회해 요청 시점에 판단한다.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 비밀번호 재설정 토큰 유효 기간. 문서에 명시되지 않아 보수적으로 30분으로 채택
     * (plan/04_Open_Questions_Log.md "Phase 1에서 새로 발견된 사항" 참고). */
    private static final long PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES = 30;

    private final UserMapper userMapper;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final String passwordResetLinkBaseUrl;

    public AuthService(UserMapper userMapper,
                        AuthMapper authMapper,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        @Value("${farmlog.password-reset.base-url:http://localhost:5173/password-reset}") String passwordResetLinkBaseUrl) {
        this.userMapper = userMapper;
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordResetLinkBaseUrl = passwordResetLinkBaseUrl;
    }

    @Transactional
    public AuthTokenResponse signUp(SignUpRequest request) {
        String email = normalizeEmail(request.email());
        userMapper.findByEmail(email).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATE);
        });

        LocalDateTime now = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .status("ACTIVE")
                .createdAt(now)
                .build();
        userMapper.insert(user);

        // 가입 즉시 사용하는 개인(PERSONAL) 조직을 자동 생성한다(4.4 "가입 즉시 로그인 처리하고
        // M-04(첫 농장 설정)로 이동" 흐름을 뒷받침). role=OWNER 근거는 OrganizationMemberEntity 참고.
        OrganizationEntity organization = OrganizationEntity.builder()
                .name(request.displayName() + " 개인 조직")
                .orgType(OrganizationEntity.ORG_TYPE_PERSONAL)
                .status(OrganizationEntity.STATUS_ACTIVE)
                .createdBy(user.getId())
                .createdAt(now)
                .build();
        authMapper.insertOrganization(organization);

        OrganizationMemberEntity member = OrganizationMemberEntity.builder()
                .organizationId(organization.getId())
                .userId(user.getId())
                .role(OrganizationMemberEntity.ROLE_OWNER)
                .status(OrganizationMemberEntity.STATUS_ACTIVE)
                .createdAt(now)
                .build();
        authMapper.insertOrganizationMember(member);

        log.info("신규 회원가입: userId={}, organizationId={}", user.getId(), organization.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserEntity user = userMapper.findByEmail(email)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        userMapper.updateLastLoginAt(user.getId(), LocalDateTime.now());
        return issueTokens(user);
    }

    @Transactional
    public AuthTokenResponse refresh(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "갱신 토큰이 전달되지 않았습니다.");
        }

        Claims claims = parseRefreshClaims(rawRefreshToken);

        String tokenHash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshTokenEntity storedToken = authMapper.findRefreshTokenByHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (!storedToken.isUsable(now)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "이미 만료되었거나 폐기된 갱신 토큰입니다. 다시 로그인해주세요.");
        }

        Long userId = jwtTokenProvider.getUserId(claims);
        UserEntity user = userMapper.findById(userId)
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        // Refresh Token 회전(rotation): 새 토큰을 먼저 발급/저장한 뒤 기존 토큰을 폐기 처리하고
        // replaced_by_token_id로 연결한다 — schema.sql의 refresh_token.replaced_by_token_id 용도.
        TokenIssueResult issued = issueTokensInternal(user);
        authMapper.revokeRefreshToken(storedToken.getId(), now, issued.refreshTokenEntity().getId());

        return issued.response();
    }

    @Transactional
    public MessageResponse requestPasswordReset(PasswordResetRequestRequest request) {
        String email = normalizeEmail(request.email());
        Optional<UserEntity> userOpt = userMapper.findByEmail(email).filter(UserEntity::isActive);

        userOpt.ifPresent(user -> {
            String rawToken = TokenHasher.randomOpaqueToken();
            LocalDateTime now = LocalDateTime.now();
            PasswordResetTokenEntity tokenEntity = PasswordResetTokenEntity.builder()
                    .userId(user.getId())
                    .tokenHash(TokenHasher.sha256Hex(rawToken))
                    .expiresAt(now.plusMinutes(PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES))
                    .createdAt(now)
                    .build();
            authMapper.insertPasswordResetToken(tokenEntity);

            // SMTP 미구성 범위(4.4) — 실제 이메일 발송 대신 콘솔(INFO 로그)로 재설정 링크를 대체 출력한다.
            log.info("[비밀번호 재설정 링크] {} -> {}?token={} (만료: {}분 후)",
                    user.getEmail(), passwordResetLinkBaseUrl, rawToken, PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES);
        });

        // 계정 존재 여부를 노출하지 않기 위해 이메일 존재 여부와 무관하게 동일한 응답을 반환한다(M-03).
        return new MessageResponse("입력하신 이메일이 등록되어 있다면 비밀번호 재설정 안내를 보냈습니다.");
    }

    @Transactional
    public MessageResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        String tokenHash = TokenHasher.sha256Hex(request.token());
        PasswordResetTokenEntity tokenEntity = authMapper.findPasswordResetTokenByHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "재설정 링크가 유효하지 않습니다. 다시 요청해주세요."));

        LocalDateTime now = LocalDateTime.now();
        if (!tokenEntity.isUsable(now)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "재설정 링크가 만료되었거나 이미 사용되었습니다. 다시 요청해주세요.");
        }

        UserEntity user = userMapper.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        userMapper.updatePasswordHash(user.getId(), passwordEncoder.encode(request.newPassword()), now);
        authMapper.markPasswordResetTokenUsed(tokenEntity.getId(), now);
        // 보안 강화 조치(문서에 명시되지 않아 보수적으로 채택): 비밀번호가 바뀌면 기존에 발급된 모든
        // Refresh Token(다른 기기의 로그인 세션 포함)을 함께 폐기해 재로그인을 강제한다.
        authMapper.revokeAllRefreshTokensForUser(user.getId(), now);

        log.info("비밀번호 재설정 완료: userId={}", user.getId());
        return new MessageResponse("비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.");
    }

    private Claims parseRefreshClaims(String rawRefreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(rawRefreshToken);
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(ErrorCode.AUTH_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return claims;
    }

    private AuthTokenResponse issueTokens(UserEntity user) {
        return issueTokensInternal(user).response();
    }

    private TokenIssueResult issueTokensInternal(UserEntity user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());

        LocalDateTime now = LocalDateTime.now();
        RefreshTokenEntity tokenEntity = RefreshTokenEntity.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.sha256Hex(refreshToken))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtTokenProvider.getRefreshTokenExpirationSeconds()))
                .build();
        authMapper.insertRefreshToken(tokenEntity);

        AuthTokenResponse response = new AuthTokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                jwtTokenProvider.getRefreshTokenExpirationSeconds(),
                new AuthTokenResponse.UserSummary(user.getId(), user.getEmail(), user.getDisplayName())
        );
        return new TokenIssueResult(response, tokenEntity);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record TokenIssueResult(AuthTokenResponse response, RefreshTokenEntity refreshTokenEntity) {
    }
}
