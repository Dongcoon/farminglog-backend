package com.farmlog.records;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;

import java.util.Map;
import java.util.Set;

public enum RecordType {
    WORK("work-logs", "workDate", Set.of("workDate", "createdAt", "updatedAt")),
    PEST_CONTROL("pest-control-logs", "applyDate", Set.of("applyDate", "createdAt", "updatedAt")),
    HARVEST("harvest-logs", "harvestDate", Set.of("harvestDate", "quantity", "createdAt", "updatedAt")),
    SALES("sales-logs", "salesDate", Set.of("salesDate", "quantity", "unitPrice", "grossAmount", "netAmount", "createdAt", "updatedAt"));

    private static final Map<String, RecordType> BY_PATH = Map.of(
            "work-logs", WORK, "pest-control-logs", PEST_CONTROL,
            "harvest-logs", HARVEST, "sales-logs", SALES);
    private final String path;
    private final String defaultSort;
    private final Set<String> allowedSorts;

    RecordType(String path, String defaultSort, Set<String> allowedSorts) {
        this.path = path; this.defaultSort = defaultSort; this.allowedSorts = allowedSorts;
    }
    public String path() { return path; }
    public String defaultSort() { return defaultSort; }
    public boolean allowsSort(String field) { return allowedSorts.contains(field); }
    public static RecordType fromPath(String path) {
        RecordType type = BY_PATH.get(path);
        if (type == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 기록 유형입니다.");
        return type;
    }
}
