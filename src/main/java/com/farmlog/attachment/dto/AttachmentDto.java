package com.farmlog.attachment.dto;

import java.time.LocalDateTime;

public record AttachmentDto(
    Long id,
    Long version,
    Long farmId,
    String clientFileId,
    String originalFileName,
    String contentType,
    Long fileSize,
    Actor createdBy,
    LocalDateTime createdAt,
    boolean canDelete) {
  public record Actor(Long id, String displayName) {}
}
