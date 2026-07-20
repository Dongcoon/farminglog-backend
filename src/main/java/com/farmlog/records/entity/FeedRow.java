package com.farmlog.records.entity;

import com.farmlog.records.RecordType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedRow {
  private RecordType type;
  private Long id, farmId, zoneId, createdBy, careAssignmentId;
  private LocalDate recordDate;
  private String zoneName,
      zoneActiveYn,
      primaryLabel,
      primaryValue,
      createdByName,
      createdRole,
      farmerConfirmStatus;
  private LocalDateTime createdAt;
}
