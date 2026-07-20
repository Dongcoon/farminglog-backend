package com.farmlog.records.dto;

import com.farmlog.records.RecordType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FeedResponse(RecordType recordType, Long id, LocalDate recordDate,
                           RecordResponse.Reference zone, String primaryLabel, String primaryValue,
                           RecordResponse.Actor createdBy, String createdRole, boolean managerInput,
                           String farmerConfirmStatus, LocalDateTime createdAt, boolean canEdit, boolean canDelete) {}
