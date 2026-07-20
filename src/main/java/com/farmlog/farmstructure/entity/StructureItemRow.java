package com.farmlog.farmstructure.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class StructureItemRow {
  private Long id; private Long eventId; private String itemType; private Long sourceFarmId; private Long targetFarmId;
  private Long zoneId; private String resourceType; private Long sourceResourceId; private Long targetResourceId;
  private String action; private LocalDate periodStart; private LocalDate periodEndExclusive;
  private String beforeJson; private String afterJson; private LocalDateTime createdAt;
  private String eventType; private String reportPolicy; private LocalDate eventEffectiveDate;
  private LocalDateTime eventConfirmedAt; private Long eventReversedByEventId;
  private LocalDate policyStart; private LocalDate policyEndExclusive;
}
