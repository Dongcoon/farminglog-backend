package com.farmlog.export.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 네 기록을 XLSX/PDF 표로 렌더링하기 위한 내부 superset 행. */
@Getter @Setter
public class ExportRecordRow {
    private Long id;
    private LocalDate recordDate;
    private String zoneName, cropName, varietyName, seasonName, workTypeName, customerName;
    private BigDecimal workerCount, workHours, amountValue, quantity, unitPrice, grossAmount, feeAmount, netAmount;
    private String chemicalName, targetPest, dilutionRatio, amountUnit, grade, unit, packageUnit, itemName;
    private Integer preharvestIntervalDays;
    private String settlementStatus, memo, farmerConfirmStatus, createdByName;
    private LocalDateTime createdAt;
}
