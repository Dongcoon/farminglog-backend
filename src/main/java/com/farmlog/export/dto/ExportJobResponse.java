package com.farmlog.export.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.farmlog.export.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExportJobResponse(Long id, Long farmId, ExportFormat format, List<ExportScope> scopes,
        LocalDate dateFrom, LocalDate dateTo, ConfirmationFilter confirmationFilter, ExportStatus status,
        Actor requestedBy, LocalDateTime requestedAt, LocalDateTime startedAt, LocalDateTime completedAt,
        LocalDateTime expiresAt, String fileName, Long fileSize, boolean canDownload,
        String errorCode, String errorMessage) {
    public record Actor(Long id, String displayName) {}
}
