package com.farmlog.records.entity;

import com.farmlog.records.RecordType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 네 기록 테이블의 공통/도메인 필드를 담는 내부 행 모델. API 응답은 별도 DTO로 제한한다. */
@Getter @Setter
public class RecordRow {
    private RecordType type;
    private Long id, organizationId, farmId, zoneId, cropId, varietyId, seasonId, workTypeId, customerId;
    private LocalDate recordDate;
    private String chemicalName, targetPest, dilutionRatio, amountUnit, grade, unit, packageUnit;
    private String itemName, settlementStatus, memo;
    private BigDecimal workerCount, workHours, amountValue, quantity, unitPrice, grossAmount, feeAmount, netAmount;
    private Integer preharvestIntervalDays;
    private String netAmountOverriddenYn;
    private Long createdBy, careAssignmentId, farmerConfirmedBy, updatedBy;
    private String createdRole, farmerConfirmStatus, clientRequestId, createdByName;
    private LocalDateTime farmerConfirmedAt, createdAt, updatedAt, deletedAt;
    private Long version;
    private ReferenceRow zone, crop, variety, season, workType, customer;
}
