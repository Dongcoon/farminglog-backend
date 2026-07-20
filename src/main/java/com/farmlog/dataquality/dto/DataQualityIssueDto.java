package com.farmlog.dataquality.dto;

import java.time.*;
import java.util.List;

public record DataQualityIssueDto(
    Long id,
    Long farmId,
    String farmName,
    Reference zone,
    LocalDate issueDate,
    String issueType,
    String issueStatus,
    String severity,
    String title,
    String description,
    String detectedBy,
    RelatedRecord relatedRecord,
    RelatedRecord resolutionRecord,
    String farmerConfirmStatus,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime closedAt,
    Long version,
    boolean canEdit,
    boolean canResolve,
    List<AttachmentSummary> attachments) {
  public record Reference(Long id, String name) {}

  public record RelatedRecord(String domain, Long id, String label, LocalDate recordDate) {}

  public record AttachmentSummary(
      Long id,
      String originalFileName,
      String contentType,
      Long fileSize,
      LocalDateTime createdAt) {}
}
