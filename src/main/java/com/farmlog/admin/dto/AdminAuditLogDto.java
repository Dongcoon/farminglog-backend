package com.farmlog.admin.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record AdminAuditLogDto(
    Long id,
    Organization organization,
    Farm farm,
    Actor actor,
    String action,
    String targetType,
    Long targetId,
    String ipAddressMasked,
    String userAgent,
    Map<String, Object> detail,
    LocalDateTime createdAt) {
  public record Organization(Long id, String name) {}

  public record Farm(Long id, String name) {}

  public record Actor(Long id, String displayName, String maskedEmail) {}
}
