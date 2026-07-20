package com.farmlog.attachment.entity;

import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhotoBatchRow {
  private String id, clientRequestId, status, commitRequestId, estimatedRecordType, memo;
  private Long organizationId, farmId, ownerUserId, careAssignmentId, zoneId, issueId;
  private int fileCount;
  private LocalDate issueDate;
  private LocalDateTime expiresAt, createdAt, committedAt;
}
