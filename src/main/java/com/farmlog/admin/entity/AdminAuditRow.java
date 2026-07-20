package com.farmlog.admin.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminAuditRow {
  private Long id, organizationId, farmId, actorUserId, targetId;
  private String organizationName,
      farmName,
      actorName,
      actorEmail,
      action,
      targetType,
      ipAddress,
      userAgent,
      detailJson;
  private LocalDateTime createdAt;
}
