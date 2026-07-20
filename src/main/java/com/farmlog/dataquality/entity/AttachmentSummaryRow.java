package com.farmlog.dataquality.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachmentSummaryRow {
  private Long id, fileSize;
  private String originalFileName, contentType;
  private LocalDateTime createdAt;
}
