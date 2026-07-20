package com.farmlog.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 단위가 다른 수량을 절대 합치지 않는 Phase 5 월간 리포트 계약. */
public record MonthlyReportResponse(
        Long farmId, String farmName, String month, LocalDate periodStart, LocalDate periodEnd, String basis,
        Long eventId, LocalDateTime structureSnapshotAt, Long structureSnapshotEventId,
        long includedRecordCount, RecordCounts recordCounts, List<UnitQuantity> harvestByUnit,
        BigDecimal grossSalesAmount, BigDecimal feeAmount, BigDecimal netSalesAmount,
        List<AverageUnitPrice> averageUnitPrices, Changes changes,
        List<HarvestBreakdown> byZone, List<HarvestBreakdown> byVariety,
        List<CustomerBreakdown> byCustomer, long managerInputCount) {

    public record RecordCounts(long work, long pestControl, long harvest, long sales) {}
    public record UnitQuantity(String unit, BigDecimal quantity) {}
    public record AverageUnitPrice(String unit, BigDecimal soldQuantity, BigDecimal grossSalesAmount,
                                   BigDecimal averageUnitPrice) {}
    public record Changes(List<HarvestChange> harvestByUnit, Change grossSales, Change netSales) {}
    public record HarvestChange(String unit, BigDecimal quantity, Change change) {}
    public record Change(BigDecimal current, BigDecimal previous, BigDecimal absoluteChange,
                         BigDecimal ratePercent, ChangeStatus status) {}
    public enum ChangeStatus { UP, DOWN, SAME, NEW, NO_DATA }
    public record HarvestBreakdown(Long id, String name, List<UnitQuantity> harvestByUnit,
                                   long recordCount, long managerInputCount) {}
    public record CustomerBreakdown(Long id, String name, BigDecimal grossSalesAmount, BigDecimal feeAmount,
                                    BigDecimal netSalesAmount, long recordCount, long managerInputCount) {}
}
