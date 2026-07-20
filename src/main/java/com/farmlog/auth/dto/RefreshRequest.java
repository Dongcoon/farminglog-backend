package com.farmlog.auth.dto;

/**
 * 모바일 클라이언트는 Refresh Token을 SecureStore에 보관하고 이 필드로 전달한다(4.4).
 * PC 웹은 httpOnly 쿠키로 전달하므로 이 필드를 비워 두어도 된다 — 컨트롤러가 쿠키를 우선 대체 조회한다.
 */
public record RefreshRequest(String refreshToken) {
}
