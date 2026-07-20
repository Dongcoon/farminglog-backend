package com.farmlog.farmstructure.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class StructureEventRow {
  private Long id; private Long organizationId; private String organizationName; private String organizationType;
  private String organizationStatus; private String eventType; private String eventName; private LocalDate effectiveDate;
  private String status; private Long version; private String clientRequestId; private String confirmRequestId;
  private String requestHash; private String confirmRequestHash; private String previewHash; private String confirmedStateHash;
  private LocalDateTime previewExpiresAt; private String requestSnapshotJson; private String impactJson;
  private String conflictsJson; private String warningsJson; private String reportPolicy; private LocalDate periodStart;
  private LocalDate periodEndExclusive; private Long reversesEventId; private Long reversedByEventId; private String reason;
  private Long createdBy; private String createdByName; private LocalDateTime createdAt; private Long confirmedBy;
  private String confirmedByName; private LocalDateTime confirmedAt; private Long canceledBy; private LocalDateTime canceledAt;
}
