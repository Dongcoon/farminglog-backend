package com.farmlog.export;

/** Lease를 잃은 이전 워커가 새 워커의 결과를 덮어쓰지 못하도록 중단시키는 내부 예외다. */
public class StaleExportClaimException extends RuntimeException {
    public StaleExportClaimException(Long jobId) {
        super("Export job claim is stale: " + jobId);
    }
}
