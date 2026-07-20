package com.farmlog.attachment.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhotoBatchFileRow {
  private Long id, fileSize;
  private String batchId,
      clientFileId,
      originalFileName,
      storedFileName,
      contentType,
      stagingPath,
      status;
  private LocalDateTime uploadedAt;
}
