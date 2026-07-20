package com.farmlog.dataquality.dto;

import java.time.LocalDateTime;

public record FollowupDto(
    Long id,
    Long issueId,
    Actor manager,
    String contactMethod,
    String followupStatus,
    String contentSummary,
    LocalDateTime nextActionAt,
    LocalDateTime createdAt) {
  public record Actor(Long id, String displayName) {}
}
