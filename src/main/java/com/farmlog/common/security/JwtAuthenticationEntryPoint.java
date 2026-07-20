package com.farmlog.common.security;

import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 인증되지 않은 요청이 보호된 API에 접근할 때 plan/02_Development_Guide.md 4.3 공통 에러 포맷으로
 * 401 응답을 내려준다. {@link JwtAuthenticationFilter}가 남긴 실패 사유(만료/위조/누락)를 우선 사용하고,
 * 없으면(=토큰 자체가 없는 비로그인 접근) AUTH_INVALID_TOKEN으로 처리한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        ErrorCode errorCode = attribute instanceof ErrorCode code ? code : ErrorCode.AUTH_INVALID_TOKEN;
        String traceId = UUID.randomUUID().toString();

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode, traceId)));
    }
}
