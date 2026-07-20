package com.farmlog.export.mapper;

import com.farmlog.export.*;
import com.farmlog.export.entity.*;
import com.farmlog.report.entity.AveragePriceRow;
import com.farmlog.report.entity.RecordCountRow;
import com.farmlog.report.entity.SalesComparisonRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ExportMapper {
    Optional<ExportJobRow> findByClientRequestId(@Param("farmId") Long farmId, @Param("clientRequestId") String clientRequestId);
    Optional<ExportJobRow> findByClientRequestIdForUpdate(@Param("farmId") Long farmId, @Param("clientRequestId") String clientRequestId);
    Optional<ExportJobRow> findById(@Param("farmId") Long farmId, @Param("id") Long id);
    Optional<ExportJobRow> findWorkerJob(@Param("id") Long id);
    List<ExportJobRow> findPage(@Param("farmId") Long farmId, @Param("offset") int offset, @Param("size") int size);
    long countPage(@Param("farmId") Long farmId);
    int insertJob(ExportJobRow row);

    long countRows(@Param("scope") ExportScope scope, @Param("farmId") Long farmId,
                   @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo,
                   @Param("confirmationFilter") ConfirmationFilter confirmationFilter);

    Optional<ExportJobRow> findClaimCandidate(@Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);
    List<ExportJobRow> findExhaustedStale(@Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);
    int markProcessing(@Param("id") Long id, @Param("claimToken") String claimToken,
                       @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
    int renewLease(@Param("id") Long id, @Param("claimToken") String claimToken, @Param("leaseUntil") LocalDateTime leaseUntil);
    int requeue(@Param("id") Long id, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                @Param("now") LocalDateTime now, @Param("claimToken") String claimToken);
    int markFailed(@Param("id") Long id, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                   @Param("now") LocalDateTime now, @Param("claimToken") String claimToken);
    int markExhaustedFailed(@Param("id") Long id, @Param("errorCode") String errorCode,
                            @Param("errorMessage") String errorMessage, @Param("now") LocalDateTime now,
                            @Param("maxAttempts") int maxAttempts);
    int markCompleted(@Param("id") Long id, @Param("fileId") Long fileId, @Param("completedAt") LocalDateTime completedAt,
                      @Param("expiresAt") LocalDateTime expiresAt, @Param("claimToken") String claimToken);
    int markExpired(@Param("id") Long id, @Param("now") LocalDateTime now);
    List<ExportJobRow> findExpiredCompleted(@Param("now") LocalDateTime now, @Param("limit") int limit);
    void insertFile(ExportFileRow row);
    int softDeleteFile(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Options(fetchSize = 500)
    Cursor<ExportRecordRow> streamRecords(@Param("scope") ExportScope scope, @Param("farmId") Long farmId,
            @Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo,
            @Param("confirmationFilter") ConfirmationFilter confirmationFilter);

    List<RecordCountRow> findSummaryCounts(@Param("farmId") Long farmId, @Param("dateFrom") LocalDate dateFrom,
                                            @Param("dateTo") LocalDate dateTo,
                                            @Param("confirmationFilter") ConfirmationFilter confirmationFilter);
    List<AveragePriceRow> findSummaryHarvest(@Param("farmId") Long farmId, @Param("dateFrom") LocalDate dateFrom,
                                             @Param("dateTo") LocalDate dateTo,
                                             @Param("confirmationFilter") ConfirmationFilter confirmationFilter);
    SalesComparisonRow findSummarySales(@Param("farmId") Long farmId, @Param("dateFrom") LocalDate dateFrom,
                                        @Param("dateTo") LocalDate dateTo,
                                        @Param("confirmationFilter") ConfirmationFilter confirmationFilter);
    List<AveragePriceRow> findSummaryAveragePrices(@Param("farmId") Long farmId, @Param("dateFrom") LocalDate dateFrom,
                                                   @Param("dateTo") LocalDate dateTo,
                                                   @Param("confirmationFilter") ConfirmationFilter confirmationFilter);

    void insertAudit(@Param("organizationId") Long organizationId, @Param("farmId") Long farmId,
                     @Param("actorUserId") Long actorUserId, @Param("action") String action,
                     @Param("targetId") Long targetId, @Param("detailJson") String detailJson,
                     @Param("now") LocalDateTime now);
}
