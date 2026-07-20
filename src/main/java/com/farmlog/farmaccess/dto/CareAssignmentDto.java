package com.farmlog.farmaccess.dto;

import java.time.LocalDateTime;

public record CareAssignmentDto(
    Long id,
    Long version,
    Farm farm,
    Manager manager,
    Actor assignedBy,
    String assignmentType,
    String permissionScope,
    String status,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    LocalDateTime assignedAt,
    LocalDateTime revokedAt,
    Actor revokedBy,
    String revokedReason,
    String memo,
    long recentInputCount,
    LocalDateTime lastInputAt,
    long openIssueCount,
    boolean canRevoke) {
  public record Farm(Long id, String name) {}

  public record Manager(Long id, String displayName, String email) {}

  public record Actor(Long id, String displayName) {}
}
