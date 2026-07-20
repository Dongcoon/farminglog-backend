package com.farmlog.common.exception;

/**
 * plan/02_Development_Guide.md 4.3 공통 에러 응답 포맷.
 */
public record ErrorResponse(String code, String message, String traceId) {

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage(), traceId);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        return new ErrorResponse(errorCode.name(), message, traceId);
    }
}
