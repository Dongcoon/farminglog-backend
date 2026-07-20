package com.farmlog.admin.dto;

import java.time.LocalDateTime;

public record AdminUserDto(
    Long id,
    String maskedEmail,
    String displayName,
    String status,
    long activeOrganizationCount,
    long activeFarmCount,
    long ownedActiveFarmCount,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt,
    LocalDateTime deletedAt) {}
