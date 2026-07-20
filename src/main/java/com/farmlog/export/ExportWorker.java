package com.farmlog.export;

import com.farmlog.export.entity.ExportJobRow;
import com.farmlog.export.entity.GeneratedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExportWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportWorker.class);
    private final ExportJobCoordinator coordinator;
    private final ExportFileGenerator generator;
    private final ExportStorage storage;

    public ExportWorker(ExportJobCoordinator coordinator, ExportFileGenerator generator, ExportStorage storage) {
        this.coordinator = coordinator;
        this.generator = generator;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${farmlog.export.poll-delay-ms:1000}")
    public void runNext() {
        var claimed = coordinator.claim();
        if (claimed.isEmpty()) return;
        ExportJobRow job = claimed.get();
        GeneratedFile generated = null;
        try {
            generated = generator.generate(job);
            coordinator.complete(job, generated);
        } catch (StaleExportClaimException ex) {
            // DB 완료가 stale claim으로 거부되면 이미 publish된 물리 파일도 반드시 보상 삭제한다.
            if (generated != null) storage.deleteQuietly(generated.storagePath().toString());
            log.info("export job {} lost its lease; stale result discarded", job.getId());
        } catch (ExportGenerationException ex) {
            log.warn("export job {} failed: {}", job.getId(), ex.code());
            transitionFailure(job, ex.code(), ex.safeMessage(), ex.retryable());
        } catch (Exception ex) {
            if (generated != null) storage.deleteQuietly(generated.storagePath().toString());
            log.error("export job {} unexpected failure", job.getId(), ex);
            transitionFailure(job, "GENERATION_FAILED", "파일 생성에 실패했습니다. 다시 요청해 주세요.", true);
        }
    }

    private void transitionFailure(ExportJobRow job, String code, String message, boolean retryable) {
        try {
            coordinator.failOrRetry(job, code, message, retryable);
        } catch (StaleExportClaimException stale) {
            log.info("export job {} failure ignored because its lease was reassigned", job.getId());
        }
    }

    @Scheduled(fixedDelayString = "${farmlog.export.cleanup-delay-ms:3600000}",
            initialDelayString = "${farmlog.export.cleanup-initial-delay-ms:60000}")
    public void expireFiles() {
        for (var file : coordinator.expireBatch()) storage.deleteQuietly(file.storagePath());
    }
}
