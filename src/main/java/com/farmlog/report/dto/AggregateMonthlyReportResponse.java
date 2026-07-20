package com.farmlog.report.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record AggregateMonthlyReportResponse(
    String month,
    LocalDate periodStart,
    LocalDate periodEnd,
    String basis,
    LocalDateTime snapshotAt,
    long farmCount,
    long farmsWithDataCount,
    long includedRecordCount,
    MonthlyReportResponse.RecordCounts recordCounts,
    List<MonthlyReportResponse.UnitQuantity> harvestByUnit,
    BigDecimal grossSalesAmount,
    BigDecimal feeAmount,
    BigDecimal netSalesAmount,
    List<MonthlyReportResponse.AverageUnitPrice> averageUnitPrices,
    MonthlyReportResponse.Changes changes,
    long managerInputCount,
    List<FarmReport> farms) {
  public record FarmReport(
      Long farmId,
      String farmName,
      boolean hasData,
      long includedRecordCount,
      MonthlyReportResponse.RecordCounts recordCounts,
      List<MonthlyReportResponse.UnitQuantity> harvestByUnit,
      BigDecimal grossSalesAmount,
      BigDecimal feeAmount,
      BigDecimal netSalesAmount,
      List<MonthlyReportResponse.AverageUnitPrice> averageUnitPrices,
      long managerInputCount) {}
}
