package com.farmlog.admin.dto;

import java.time.LocalDateTime;

public record AdminFarmDto(
    Long id,
    String farmCode,
    String name,
    Organization organization,
    Owner owner,
    String addressSummary,
    String lifecycleStatus,
    String status,
    long activeMemberCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  public record Organization(Long id, String name, String orgType) {}

  public record Owner(Long id, String displayName, String maskedEmail) {}
}
