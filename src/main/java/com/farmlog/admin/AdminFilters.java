package com.farmlog.admin;

import java.time.LocalDateTime;

public final class AdminFilters {
  private AdminFilters() {}

  public record Users(
      String q, String status, Long organizationId, int page, int size, String sort) {}

  public record Farms(
      String q,
      Long organizationId,
      Long ownerUserId,
      String lifecycleStatus,
      String status,
      int page,
      int size,
      String sort) {}

  public record Audits(
      Long organizationId,
      Long farmId,
      Long actorUserId,
      String action,
      String targetType,
      Long targetId,
      LocalDateTime createdFrom,
      LocalDateTime createdToExclusive,
      int page,
      int size,
      String sort) {}

  public record RequestMeta(String ipAddress, String userAgent) {}
}
