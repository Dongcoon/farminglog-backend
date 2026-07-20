package com.farmlog.farmaccess.dto;

import java.time.LocalDateTime;

public record MemberDto(
    Long id,
    Long farmId,
    User user,
    String role,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version,
    boolean canChangeRole,
    boolean canDeactivate) {
  public record User(Long id, String email, String displayName) {}
}
