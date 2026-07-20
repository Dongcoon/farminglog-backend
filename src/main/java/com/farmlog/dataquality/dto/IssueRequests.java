package com.farmlog.dataquality.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class IssueRequests {
  private IssueRequests() {}

  public record Create(
      @NotNull LocalDate issueDate,
      Long zoneId,
      @NotBlank String issueType,
      String severity,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 4000) String description,
      String relatedDomain,
      Long relatedRecordId) {}

  public record Update(@NotBlank String issueStatus, @NotNull Long version) {}
}
