package com.farmlog.admin.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserRow {
  private Long id;
  private String email, displayName, status;
  private long activeOrganizationCount, activeFarmCount, ownedActiveFarmCount;
  private LocalDateTime lastLoginAt, createdAt, deletedAt;
}
