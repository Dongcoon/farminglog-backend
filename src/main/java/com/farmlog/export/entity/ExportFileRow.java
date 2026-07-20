package com.farmlog.export.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class ExportFileRow {
    private Long id, organizationId, farmId, refId, fileSize, createdBy;
    private String refType, originalFileName, storedFileName, contentType, storageType, storagePath;
    private LocalDateTime createdAt;
}
