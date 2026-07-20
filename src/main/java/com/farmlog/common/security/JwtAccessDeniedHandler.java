package com.farmlog.common.security;

import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 인증은 되었지만 권한이 부족한 요청(예: 역할 기반 접근 제어 실패)에 대해 공통 에러 포맷으로 403을
 * 내려준다. Phase 1에는 역할 기반 인가가 아직 없어 실제로 발생하지 않지만, Phase 2 이후 tenant/역할
 * 검증 로직이 AccessDeniedException을 던지기 시작할 때 바로 재사용할 수 있도록 미리 준비해 둔다.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        String traceId = UUID.randomUUID().toString();
        response.setStatus(ErrorCode.FORBIDDEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.FORBIDDEN, traceId)));
    }
}
