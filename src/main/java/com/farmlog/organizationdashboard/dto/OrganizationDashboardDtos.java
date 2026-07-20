package com.farmlog.organizationdashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** W-11 조직 관리자 전용 조회 API의 개인정보 최소화 응답 계약. */
public final class OrganizationDashboardDtos {
  private OrganizationDashboardDtos() {}

  public record OrganizationIdentity(Long id, String name, String orgType, String status) {}
  public record RecordCounts(long work, long pestControl, long harvest, long sales) {}
  public record UnitQuantity(String unit, BigDecimal quantity) {}
  public record AverageUnitPrice(String unit, BigDecimal soldQuantity,
                                 BigDecimal grossSalesAmount, BigDecimal averageUnitPrice) {}
  public enum ChangeStatus { UP, DOWN, SAME, NEW, NO_DATA }
  public record Change(BigDecimal current, BigDecimal previous, BigDecimal absoluteChange,
                       BigDecimal ratePercent, ChangeStatus status) {}
  public record HarvestChange(String unit, BigDecimal quantity, Change change) {}
  public record Changes(List<HarvestChange> harvestByUnit, Change grossSales, Change netSales) {}
  public record Owner(Long id, String displayName, String maskedEmail) {}
  public record MainCrop(Long id, String name) {}

  public record DashboardResponse(
      OrganizationIdentity organization, String month, LocalDate periodStart, LocalDate periodEnd,
      String basis, LocalDateTime snapshotAt, long farmCount, long activeFarmCount,
      long farmsWithDataCount, long includedRecordCount, RecordCounts recordCounts,
      List<UnitQuantity> harvestByUnit, BigDecimal grossSalesAmount, BigDecimal feeAmount,
      BigDecimal netSalesAmount, List<AverageUnitPrice> averageUnitPrices, Changes changes,
      long managerInputCount) {}

  public record FarmRowResponse(
      Long id, String farmCode, String name, String addressSummary, String lifecycleStatus,
      String status, Owner owner, MainCrop mainCrop, boolean hasData, long includedRecordCount,
      RecordCounts recordCounts, List<UnitQuantity> harvestByUnit, BigDecimal grossSalesAmount,
      BigDecimal feeAmount, BigDecimal netSalesAmount, long managerInputCount,
      LocalDateTime createdAt, LocalDateTime updatedAt) {}

  public record FarmIdentity(
      Long id, String farmCode, String name, String addressSummary, String lifecycleStatus,
      String status, Owner owner, MainCrop mainCrop, LocalDateTime createdAt,
      LocalDateTime updatedAt, long activeMemberCount, long activeZoneCount) {}

  public record DetailMonthly(
      String month, long includedRecordCount, RecordCounts recordCounts,
      List<UnitQuantity> harvestByUnit, BigDecimal grossSalesAmount, BigDecimal feeAmount,
      BigDecimal netSalesAmount, List<AverageUnitPrice> averageUnitPrices, Changes changes,
      long managerInputCount) {}

  public record FarmDetailResponse(FarmIdentity farm, DetailMonthly monthly,
                                   LocalDateTime snapshotAt) {}
}
