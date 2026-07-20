package com.farmlog.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlog.export.entity.ExportFileRow;
import com.farmlog.export.entity.ExportJobRow;
import com.farmlog.export.entity.GeneratedFile;
import com.farmlog.export.mapper.ExportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 파일 생성 I/O와 짧은 DB 상태 트랜잭션을 분리하고 claim token으로 워커 소유권을 검증한다. */
@Service
public class ExportJobCoordinator {
    private final ExportMapper mapper;
    private final ExportProperties properties;
    private final ObjectMapper objectMapper;

    public ExportJobCoordinator(ExportMapper mapper, ExportProperties properties, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<ExportJobRow> claim() {
        LocalDateTime now = LocalDateTime.now();
        // 최대 시도를 모두 사용한 채 lease가 만료된 작업은 더 이상 후보에 남지 않게 종결한다.
        for (ExportJobRow exhausted : mapper.findExhaustedStale(now, properties.getMaxAttempts())) {
            if (mapper.markExhaustedFailed(exhausted.getId(), "RETRY_EXHAUSTED",
                    "여러 번 시도했지만 파일을 만들지 못했습니다.", now, properties.getMaxAttempts()) == 1) {
                auditSystem(exhausted, "EXPORT_FAILED", "RETRY_EXHAUSTED", now);
            }
        }

        Optional<ExportJobRow> candidate = mapper.findClaimCandidate(now, properties.getMaxAttempts());
        if (candidate.isEmpty()) return Optional.empty();
        String claimToken = UUID.randomUUID().toString();
        if (mapper.markProcessing(candidate.get().getId(), claimToken, now,
                now.plusMinutes(properties.getLeaseMinutes())) != 1) return Optional.empty();
        return mapper.findWorkerJob(candidate.get().getId());
    }

    /** 생성 중인 워커만 lease를 연장할 수 있으며 이미 소유권을 잃었다면 즉시 중단한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renew(Long id, String claimToken) {
        if (mapper.renewLease(id, claimToken,
                LocalDateTime.now().plusMinutes(properties.getLeaseMinutes())) != 1) {
            throw new StaleExportClaimException(id);
        }
    }

    @Transactional
    public void complete(ExportJobRow job, GeneratedFile generated) {
        LocalDateTime now = LocalDateTime.now();
        ExportFileRow file = new ExportFileRow();
        file.setOrganizationId(job.getOrganizationId());
        file.setFarmId(job.getFarmId());
        file.setRefType("EXPORT_JOB");
        file.setRefId(job.getId());
        file.setOriginalFileName(generated.originalFileName());
        file.setStoredFileName(generated.storedFileName());
        file.setContentType(generated.contentType());
        file.setFileSize(generated.fileSize());
        file.setStorageType("LOCAL");
        file.setStoragePath(generated.storagePath().toString().replace('\\', '/'));
        file.setCreatedBy(job.getRequestedBy());
        file.setCreatedAt(now);
        mapper.insertFile(file);
        // 첨부 메타데이터도 같은 트랜잭션이므로 stale이면 함께 롤백된다.
        if (mapper.markCompleted(job.getId(), file.getId(), now,
                now.plusDays(properties.getRetentionDays()), job.getClaimToken()) != 1) {
            throw new StaleExportClaimException(job.getId());
        }
        auditSystem(job, "EXPORT_COMPLETED", null, now);
    }

    @Transactional
    public void failOrRetry(ExportJobRow job, String code, String safeMessage, boolean retryable) {
        LocalDateTime now = LocalDateTime.now();
        int affected;
        if (retryable && job.getAttemptCount() < properties.getMaxAttempts()) {
            affected = mapper.requeue(job.getId(), code, safeMessage, now, job.getClaimToken());
        } else {
            String finalCode = retryable ? "RETRY_EXHAUSTED" : code;
            String finalMessage = retryable ? "여러 번 시도했지만 파일을 만들지 못했습니다." : safeMessage;
            affected = mapper.markFailed(job.getId(), finalCode, finalMessage, now, job.getClaimToken());
            if (affected == 1) auditSystem(job, "EXPORT_FAILED", finalCode, now);
        }
        if (affected != 1) throw new StaleExportClaimException(job.getId());
    }

    @Transactional
    public List<ExpiredFile> expireBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<ExpiredFile> files = new ArrayList<>();
        for (ExportJobRow row : mapper.findExpiredCompleted(now, 100)) {
            if (mapper.markExpired(row.getId(), now) == 1) {
                if (row.getFileId() != null) mapper.softDeleteFile(row.getFileId(), now);
                files.add(new ExpiredFile(row.getStoragePath()));
                auditSystem(row, "EXPORT_EXPIRED", null, now);
            }
        }
        return files;
    }

    private void auditSystem(ExportJobRow job, String action, String code, LocalDateTime now) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("format", job.getExportType());
        detail.put("errorCode", code);
        detail.put("actorPolicy", "SYSTEM");
        String json;
        try {
            json = objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            json = "{\"actorPolicy\":\"SYSTEM\"}";
        }
        mapper.insertAudit(job.getOrganizationId(), job.getFarmId(), null, action, job.getId(), json, now);
    }

    public record ExpiredFile(String storagePath) {}
}
