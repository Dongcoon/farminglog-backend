package com.farmlog.records.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkRequest(@NotNull Operation operation,
                          @NotEmpty @Size(max = 100) List<@Valid Item> items,
                          RecordMutationRequest changes) {
    public enum Operation { UPDATE, DELETE }
    public record Item(@NotNull Long id, @NotNull Long version) {}
}
