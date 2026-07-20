package com.farmlog.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMembership;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.export.dto.ExportCreateRequest;
import com.farmlog.export.dto.ExportDownload;
import com.farmlog.export.dto.ExportJobResponse;
import com.farmlog.export.entity.ExportJobRow;
import com.farmlog.export.mapper.ExportMapper;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.entity.FarmMemberEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.records.dto.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ExportService {
    private static final Set<String> EXPORT_ROLES = Set.of(FarmMemberEntity.ROLE_FARM_OWNER,
            FarmMemberEntity.ROLE_FARM_MANAGER, FarmMemberEntity.ROLE_VIEWER);
    private final ExportMapper mapper;
    private final FarmMapper farmMapper;
    private final FarmAccessGuard accessGuard;
    private final ExportProperties properties;
    private final ExportStorage storage;
    private final ObjectMapper objectMapper;
    private final FarmMutationGuard mutationGuard;

    @Autowired
    public ExportService(ExportMapper mapper, FarmMapper farmMapper, FarmAccessGuard accessGuard,
                         ExportProperties properties, ExportStorage storage, ObjectMapper objectMapper,
                         FarmMutationGuard mutationGuard) {
        this.mapper = mapper;
        this.farmMapper = farmMapper;
        this.accessGuard = accessGuard;
        this.properties = properties;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.mutationGuard = mutationGuard;
    }

    public ExportService(ExportMapper mapper, FarmMapper farmMapper, FarmAccessGuard accessGuard,
                         ExportProperties properties, ExportStorage storage, ObjectMapper objectMapper) {
        this(mapper, farmMapper, accessGuard, properties, storage, objectMapper,
                new FarmMutationGuard(farmMapper));
    }

    @Transactional
    public ExportJobResponse create(Long userId, Long farmId, ExportCreateRequest request) {
        requireRole(userId, farmId);
        FarmEntity farm = mutationGuard.lockActiveFarm(farmId);
        accessGuard.requireFarmRoleForUpdate(userId, farmId, EXPORT_ROLES.toArray(String[]::new));
        validate(request);
        Optional<ExportJobRow> existing = mapper.findByClientRequestId(farmId, request.clientRequestId());
        if (existing.isPresent()) return response(existing.get());
        List<ExportScope> scopes = canonicalScopes(request.scopes());
        long rows = scopes.stream().filter(scope -> scope != ExportScope.REPORT_SUMMARY)
                .mapToLong(scope -> mapper.countRows(scope, farmId, request.dateFrom(), request.dateTo(),
                        request.confirmationFilter())).sum();
        int max = request.format() == ExportFormat.XLSX ? properties.getXlsxMaxRows() : properties.getPdfMaxRows();
        if (rows > max) throw new BusinessException(ErrorCode.EXPORT_ROW_LIMIT_EXCEEDED);

        ExportJobRow row = new ExportJobRow();
        row.setOrganizationId(farm.getOrganizationId());
        row.setFarmId(farmId);
        row.setExportType(request.format().name());
        row.setScopesJson(writeScopes(scopes));
        row.setConfirmationFilter(request.confirmationFilter().name());
        row.setClientRequestId(request.clientRequestId());
        row.setPeriodStart(request.dateFrom());
        row.setPeriodEnd(request.dateTo());
        row.setStatus(ExportStatus.REQUESTED.name());
        row.setRequestedBy(userId);
        row.setRequestedAt(LocalDateTime.now());
        try {
            mapper.insertJob(row);
        } catch (DuplicateKeyException duplicate) {
            // 동시 요청의 unique-key 충돌 뒤에는 snapshot read가 아닌 현재 읽기로 결과를 확인한다.
            return response(mapper.findByClientRequestIdForUpdate(farmId, request.clientRequestId())
                    .orElseThrow(() -> duplicate));
        }
        audit(row, userId, "EXPORT_REQUESTED", detail(row, null, null));
        return response(mapper.findById(farmId, row.getId()).orElse(row));
    }

    public PageResponse<ExportJobResponse> list(Long userId, Long farmId, int page, int size) {
        requireRole(userId, farmId);
        validatePage(page, size);
        return PageResponse.of(mapper.findPage(farmId, page * size, size).stream().map(this::response).toList(),
                page, size, mapper.countPage(farmId));
    }

    public ExportJobResponse detail(Long userId, Long farmId, Long id) {
        requireRole(userId, farmId);
        return response(get(farmId, id));
    }

    /* 만료/파일 누락 상태 변경은 오류 응답을 던져도 커밋되어야 한다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public ExportDownload download(Long userId, Long farmId, Long id) {
        requireRole(userId, farmId);
        ExportJobRow row = get(farmId, id);
        LocalDateTime now = LocalDateTime.now();
        ExportStatus status = ExportStatus.valueOf(row.getStatus());
        if (status == ExportStatus.EXPIRED || isExpired(row, now)) {
            expire(row, now, "RETENTION_EXPIRED");
            throw new BusinessException(ErrorCode.EXPORT_EXPIRED);
        }
        if (status == ExportStatus.FAILED) throw new BusinessException(ErrorCode.EXPORT_FAILED);
        if (status != ExportStatus.COMPLETED) throw new BusinessException(ErrorCode.EXPORT_NOT_READY);
        try {
            var path = storage.resolveExisting(row.getStoragePath());
            // 요청자가 아닌 실제 다운로드 사용자를 감사 주체로 기록한다.
            audit(row, userId, "EXPORT_DOWNLOAD", detail(row, null, null));
            return new ExportDownload(path, row.getOriginalFileName(), row.getContentType(), row.getFileSize());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.EXPORT_FILE_MISSING) {
                expire(row, now, "FILE_MISSING");
            }
            throw ex;
        }
    }

    private void expire(ExportJobRow row, LocalDateTime now, String reason) {
        if (mapper.markExpired(row.getId(), now) == 1) {
            if (row.getFileId() != null) mapper.softDeleteFile(row.getFileId(), now);
            deleteAfterCommit(row.getStoragePath());
            audit(row, null, "EXPORT_EXPIRED", detail(row, reason, "SYSTEM"));
        }
    }

    private void deleteAfterCommit(String storagePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.deleteQuietly(storagePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.deleteQuietly(storagePath); }
        });
    }

    private ExportJobRow get(Long farmId, Long id) {
        return mapper.findById(farmId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPORT_JOB_NOT_FOUND));
    }

    private FarmMembership requireRole(Long userId, Long farmId) {
        FarmMembership membership = accessGuard.requireFarmMember(userId, farmId);
        if (!EXPORT_ROLES.contains(membership.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return membership;
    }

    private void validate(ExportCreateRequest request) {
        if (request == null) throw validation("요청 본문은 필수입니다.");
        if (request.clientRequestId() == null || request.format() == null || request.dateFrom() == null
                || request.dateTo() == null || request.scopes() == null || request.scopes().isEmpty()
                || request.confirmationFilter() == null || request.scopes().stream().anyMatch(java.util.Objects::isNull)) {
            throw validation("필수 입력값을 확인해 주세요.");
        }
        try {
            UUID.fromString(request.clientRequestId());
        } catch (Exception ex) {
            throw validation("clientRequestId는 UUID 형식이어야 합니다.");
        }
        if (request.dateFrom().isAfter(request.dateTo())) throw validation("시작일은 종료일보다 늦을 수 없습니다.");
        long days = ChronoUnit.DAYS.between(request.dateFrom(), request.dateTo()) + 1;
        long max = request.format() == ExportFormat.XLSX ? 366 : 31;
        if (days > max) throw validation(request.format() + " 기간은 최대 " + max + "일입니다.");
        if (request.scopes().size() > ExportScope.values().length
                || new HashSet<>(request.scopes()).size() != request.scopes().size()) {
            throw validation("포함 범위가 중복되었습니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw validation("page는 0 이상, size는 1~100입니다.");
        }
    }

    private List<ExportScope> canonicalScopes(Collection<ExportScope> scopes) {
        return Arrays.stream(ExportScope.values()).filter(scopes::contains).toList();
    }

    private String writeScopes(List<ExportScope> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<ExportScope> readScopes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("저장된 내보내기 범위가 올바르지 않습니다.", ex);
        }
    }

    private ExportJobResponse response(ExportJobRow row) {
        LocalDateTime now = LocalDateTime.now();
        ExportStatus persisted = ExportStatus.valueOf(row.getStatus());
        // 정리 스케줄러 실행 전에도 API 계약상 만료 상태를 즉시 노출한다.
        ExportStatus status = persisted == ExportStatus.COMPLETED && isExpired(row, now)
                ? ExportStatus.EXPIRED : persisted;
        boolean canDownload = status == ExportStatus.COMPLETED && row.getFileId() != null;
        return new ExportJobResponse(row.getId(), row.getFarmId(), ExportFormat.valueOf(row.getExportType()),
                readScopes(row.getScopesJson()), row.getPeriodStart(), row.getPeriodEnd(),
                ConfirmationFilter.valueOf(row.getConfirmationFilter()), status,
                new ExportJobResponse.Actor(row.getRequestedBy(), row.getRequestedByName()), row.getRequestedAt(),
                row.getStartedAt(), row.getCompletedAt(), row.getExpiresAt(), row.getOriginalFileName(),
                row.getFileSize(), canDownload, row.getErrorCode(), safeMessage(row.getErrorCode()));
    }

    private boolean isExpired(ExportJobRow row, LocalDateTime now) {
        return row.getExpiresAt() != null && !row.getExpiresAt().isAfter(now);
    }

    private String safeMessage(String code) {
        if (code == null) return null;
        return switch (code) {
            case "ROW_LIMIT_EXCEEDED" -> "기록이 너무 많습니다. 기간이나 포함 범위를 줄여 주세요.";
            case "FONT_UNAVAILABLE" -> "PDF 한글 글꼴을 준비하지 못했습니다.";
            case "STORAGE_UNAVAILABLE" -> "파일 저장소를 사용할 수 없습니다.";
            case "RETRY_EXHAUSTED" -> "여러 번 시도했지만 파일을 만들지 못했습니다.";
            default -> "파일 생성에 실패했습니다. 다시 요청해 주세요.";
        };
    }

    private String detail(ExportJobRow row, String reason, String actorPolicy) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("format", row.getExportType());
        value.put("dateFrom", row.getPeriodStart());
        value.put("dateTo", row.getPeriodEnd());
        value.put("reason", reason);
        value.put("actorPolicy", actorPolicy);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private void audit(ExportJobRow row, Long actorUserId, String action, String json) {
        mapper.insertAudit(row.getOrganizationId(), row.getFarmId(), actorUserId, action, row.getId(), json,
                LocalDateTime.now());
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
