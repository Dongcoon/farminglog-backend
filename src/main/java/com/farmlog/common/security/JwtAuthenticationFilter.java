package com.farmlog.common.security;

import com.farmlog.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * {@code Authorization: Bearer <accessToken>} 헤더를 파싱해 Access Token을 검증하고,
 * 성공 시 {@link UserPrincipal}(userId, email)을 SecurityContext에 등록한다.
 *
 * <p>Phase 1 범위에서는 농장별 역할을 알 수 없으므로(로그인 시점에는 농장 컨텍스트가 없음), 인증된
 * 사용자에게는 공통 권한 {@code ROLE_USER}만 부여한다. 화면/버튼 단위의 역할 기반 접근 제어는 프론트엔드
 * {@code usePermission} 훅과, 농장 멤버십 API가 준비되는 Phase 2 이후 백엔드 {@code common/tenant}
 * 검증 로직으로 처리한다.</p>
 *
 * <p>토큰이 없거나(비로그인 접근) 검증에 실패해도 이 필터는 예외를 던지지 않는다. 대신 실패 사유를
 * 요청 속성({@link #AUTH_ERROR_ATTRIBUTE})에 남기고 필터 체인을 계속 진행시켜, 이후
 * {@code anyRequest().authenticated()} 규칙에 의해 Spring Security가 인증 예외를 발생시키면
 * {@link JwtAuthenticationEntryPoint}가 이 속성을 읽어 공통 에러 포맷({@code code}/{@code message}/
 * {@code traceId})으로 401 응답을 내려준다.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "com.farmlog.auth.error";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtTokenProvider.parseClaims(token);
                if (!jwtTokenProvider.isAccessToken(claims)) {
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.AUTH_INVALID_TOKEN);
                } else {
                    UserPrincipal principal = new UserPrincipal(jwtTokenProvider.getUserId(claims), jwtTokenProvider.getEmail(claims));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException ex) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.AUTH_EXPIRED);
            } catch (JwtException | IllegalArgumentException ex) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.AUTH_INVALID_TOKEN);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
