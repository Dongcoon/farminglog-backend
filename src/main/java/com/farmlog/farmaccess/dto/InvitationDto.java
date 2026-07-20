package com.farmlog.farmaccess.dto;

import java.time.LocalDateTime;

public record InvitationDto(
    Long id,
    Long farmId,
    String email,
    String role,
    String status,
    Actor invitedBy,
    LocalDateTime invitedAt,
    LocalDateTime expiresAt,
    LocalDateTime respondedAt,
    Long version,
    boolean canCancel) {
  public record Actor(Long id, String displayName) {}
}
