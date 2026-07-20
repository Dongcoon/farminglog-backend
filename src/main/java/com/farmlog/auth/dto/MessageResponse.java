package com.farmlog.auth.dto;

/** 성공 여부만 안내하면 되는 간단한 응답(비밀번호 재설정 등). */
public record MessageResponse(String message) {
}
