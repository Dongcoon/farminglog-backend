package com.farmlog.attachment.dto;

import com.farmlog.dataquality.dto.DataQualityIssueDto;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class PhotoDraftDtos {
  private PhotoDraftDtos() {}

  public record CreateRequest(
      @NotBlank String clientRequestId,
      @NotNull LocalDate issueDate,
      Long zoneId,
      @NotBlank String estimatedRecordType,
      @Size(max = 4000) String memo,
      @Min(1) @Max(5) int fileCount) {}

  public record Response(
      String batchId, String status, LocalDateTime expiresAt, List<String> uploadedClientFileIds) {}

  public record CommitRequest(@NotBlank String clientRequestId) {}

  public record CommitResponse(DataQualityIssueDto issue, List<AttachmentDto> attachments) {}
}
