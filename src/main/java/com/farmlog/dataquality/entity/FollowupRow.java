package com.farmlog.dataquality.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowupRow {
  private Long id, organizationId, farmId, issueId, managerUserId;
  private String managerName, contactMethod, followupStatus, contentSummary, clientRequestId;
  private LocalDateTime nextActionAt, createdAt;
}
