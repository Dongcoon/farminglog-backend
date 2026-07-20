package com.farmlog.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.farmlog.records.RecordType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordResponse(
        RecordType recordType, Long id, Long farmId,
        LocalDate workDate, LocalDate applyDate, LocalDate harvestDate, LocalDate salesDate,
        Reference zone, Reference crop, Reference variety, Reference season, Reference workType, Reference customer,
        BigDecimal workerCount, BigDecimal workHours,
        String chemicalName, String targetPest, String dilutionRatio, BigDecimal amountValue, String amountUnit,
        Integer preharvestIntervalDays, String grade, BigDecimal quantity, String unit, String packageUnit,
        String itemName, BigDecimal unitPrice, BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal calculatedNetAmount, BigDecimal netAmount,
        Boolean netAmountOverridden, String settlementStatus, String memo,
        Actor createdBy, String createdRole, Boolean managerInput, String farmerConfirmStatus,
        Long careAssignmentId, Actor farmerConfirmedBy, LocalDateTime farmerConfirmedAt,
        LocalDateTime createdAt, LocalDateTime updatedAt, Long version, Boolean canEdit, Boolean canDelete
) {
    public record Reference(Long id, String name, boolean activeYn) {}
    public record Actor(Long id, String displayName) {}
}
