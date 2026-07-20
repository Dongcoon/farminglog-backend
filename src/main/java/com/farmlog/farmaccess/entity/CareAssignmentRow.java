package com.farmlog.farmaccess.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CareAssignmentRow {
  private Long id, organizationId, farmId, managerUserId, assignedBy, revokedBy, version;
  private String farmName, managerName, managerEmail, assignedByName, revokedByName;
  private String assignmentType, permissionScope, status, memo, revokedReason, clientRequestId;
  private LocalDateTime startsAt, endsAt, assignedAt, revokedAt, updatedAt, lastInputAt;
  private long recentInputCount, openIssueCount;
}
