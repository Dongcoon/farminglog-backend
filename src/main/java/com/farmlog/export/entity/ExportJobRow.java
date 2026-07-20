package com.farmlog.export.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class ExportJobRow {
    private Long id, organizationId, farmId, fileId, requestedBy, fileSize;
    private String exportType, scopesJson, confirmationFilter, clientRequestId, status;
    private String errorCode, errorMessage, requestedByName;
    private String claimToken;
    private String originalFileName, storedFileName, contentType, storagePath;
    private LocalDate periodStart, periodEnd;
    private LocalDateTime requestedAt, startedAt, completedAt, expiresAt, leaseUntil, updatedAt;
    private int attemptCount;
}
