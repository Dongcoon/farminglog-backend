package com.farmlog.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BulkResponse(int affectedCount, List<RecordResponse> content, List<Long> ids) {
    public static BulkResponse updated(List<RecordResponse> content) { return new BulkResponse(content.size(), content, null); }
    public static BulkResponse deleted(List<Long> ids) { return new BulkResponse(ids.size(), null, ids); }
}
