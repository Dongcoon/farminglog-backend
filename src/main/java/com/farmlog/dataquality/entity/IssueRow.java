package com.farmlog.dataquality.entity;

import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueRow {
  private Long id,
      organizationId,
      farmId,
      zoneId,
      relatedRefId,
      resolutionRefId,
      assignedManagerUserId,
      createdBy,
      version;
  private String farmName,
      zoneName,
      issueType,
      issueStatus,
      severity,
      title,
      description,
      detectedBy;
  private String relatedRefType, resolutionRefType, farmerConfirmStatus;
  private LocalDate issueDate;
  private LocalDateTime createdAt, updatedAt, closedAt;
}
