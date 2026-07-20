package com.farmlog.admin.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminFarmRow {
  private Long id, organizationId, ownerUserId;
  private String farmCode,
      name,
      organizationName,
      orgType,
      ownerName,
      ownerEmail,
      address,
      lifecycleStatus,
      status;
  private long activeMemberCount;
  private LocalDateTime createdAt, updatedAt;
}
