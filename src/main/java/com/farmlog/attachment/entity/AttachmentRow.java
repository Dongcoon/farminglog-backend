package com.farmlog.attachment.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachmentRow {
  private Long id, organizationId, farmId, refId, fileSize, createdBy, version;
  private String refType,
      clientFileId,
      originalFileName,
      storedFileName,
      contentType,
      storagePath,
      createdByName;
  private LocalDateTime createdAt, deletedAt;
}
