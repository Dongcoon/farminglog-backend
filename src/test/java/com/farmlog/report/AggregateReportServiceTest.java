package com.farmlog.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.report.entity.AggregateAverageRow;
import com.farmlog.report.entity.AggregateFarmRow;
import com.farmlog.report.entity.AggregateHarvestRow;
import com.farmlog.report.entity.AggregateRecordCountRow;
import com.farmlog.report.entity.AggregateSalesRow;
import com.farmlog.report.mapper.ReportMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class AggregateReportServiceTest {
  private ReportMapper mapper;
  private ReportService service;

  @BeforeEach
  void setUp() {
    mapper = mock(ReportMapper.class);
    service = new ReportService(mapper, mock(FarmMapper.class), mock(FarmAccessGuard.class));
    when(mapper.findAggregateRecordCounts(anyList(), any(), any())).thenReturn(List.of());
    when(mapper.findAggregateHarvestComparison(anyList(), any(), any(), any()))
        .thenReturn(List.of());
    when(mapper.findAggregateSalesComparison(anyList(), any(), any(), any())).thenReturn(List.of());
    when(mapper.findAggregateAverageUnitPrices(anyList(), any(), any())).thenReturn(List.of());
  }

  @Test
  void requiresAtLeastOneActiveOwnerCapability() {
    when(mapper.countOwnerCapabilities(7L)).thenReturn(0L);

    assertThatThrownBy(() -> service.aggregate(7L, "2026-07", ReportService.RECORDED_STRUCTURE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    verify(mapper, never()).findAggregateFarms(7L);
  }

  @Test
  void returnsEmptyNoDataWhenCapabilityExistsButNoFarmPassesStructuralFilter() {
    when(mapper.countOwnerCapabilities(7L)).thenReturn(1L);
    when(mapper.findAggregateFarms(7L)).thenReturn(List.of());

    var result = service.aggregate(7L, "2026-07", ReportService.RECORDED_STRUCTURE);

    assertThat(result.farmCount()).isZero();
    assertThat(result.farms()).isEmpty();
    assertThat(result.grossSalesAmount()).isEqualByComparingTo("0.00");
    assertThat(result.changes().grossSales().status().name()).isEqualTo("NO_DATA");
    verify(mapper, never()).findAggregateRecordCounts(anyList(), any(), any());
  }

  @Test
  void includesZeroDataFarmAndUsesQuantityWeightedGlobalAverage() {
    when(mapper.countOwnerCapabilities(7L)).thenReturn(3L);
    when(mapper.findAggregateFarms(7L))
        .thenReturn(List.of(farm(11L, "해뜰 농장"), farm(12L, "솔밭 농장"), farm(13L, "빈 농장")));
    when(mapper.findAggregateRecordCounts(anyList(), any(), any()))
        .thenReturn(List.of(record(11L, "SALES", 1, 1), record(12L, "SALES", 1, 0)));
    when(mapper.findAggregateSalesComparison(anyList(), any(), any(), any()))
        .thenReturn(List.of(sales(11L, "280", "10", "270", "100", "90", 1, 1)));
    when(mapper.findAggregateAverageUnitPrices(anyList(), any(), any()))
        .thenReturn(List.of(average(11L, "kg", "1", "100"), average(12L, "kg", "9", "180")));

    var result = service.aggregate(7L, "2026-07", ReportService.RECORDED_STRUCTURE);

    assertThat(result.farmCount()).isEqualTo(3);
    assertThat(result.farmsWithDataCount()).isEqualTo(2);
    assertThat(result.farms())
        .extracting(item -> item.hasData())
        .containsExactly(true, true, false);
    assertThat(result.farms().get(2).includedRecordCount()).isZero();
    assertThat(result.averageUnitPrices())
        .singleElement()
        .satisfies(
            price -> {
              assertThat(price.soldQuantity()).isEqualByComparingTo("10.00");
              assertThat(price.averageUnitPrice()).isEqualByComparingTo("28.00");
            });
    verify(mapper)
        .findAggregateRecordCounts(
            List.of(11L, 12L, 13L),
            java.time.LocalDate.of(2026, 7, 1),
            java.time.LocalDate.of(2026, 8, 1));
  }

  @Test
  void sumsCurrentAndPreviousValuesAcrossSameFarmSet() {
    when(mapper.countOwnerCapabilities(7L)).thenReturn(1L);
    when(mapper.findAggregateFarms(7L)).thenReturn(List.of(farm(11L, "해뜰 농장")));
    when(mapper.findAggregateHarvestComparison(anyList(), any(), any(), any()))
        .thenReturn(List.of(harvest(11L, "kg", "15", "10", 2, 1)));
    when(mapper.findAggregateSalesComparison(anyList(), any(), any(), any()))
        .thenReturn(List.of(sales(11L, "150", "20", "130", "100", "80", 2, 1)));

    var result = service.aggregate(7L, "2026-07", ReportService.RECORDED_STRUCTURE);

    assertThat(result.changes().harvestByUnit())
        .singleElement()
        .satisfies(item -> assertThat(item.change().ratePercent()).isEqualByComparingTo("50.00"));
    assertThat(result.changes().grossSales().ratePercent()).isEqualByComparingTo("50.00");
    assertThat(result.changes().netSales().ratePercent()).isEqualByComparingTo("62.50");
  }

  @Test
  void aggregateMethodDeclaresRepeatableReadSnapshot() throws Exception {
    Transactional transactional =
        ReportService.class
            .getMethod("aggregate", Long.class, String.class, String.class)
            .getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.readOnly()).isTrue();
    assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
  }

  private AggregateFarmRow farm(long id, String name) {
    AggregateFarmRow row = new AggregateFarmRow();
    row.setFarmId(id);
    row.setFarmName(name);
    return row;
  }

  private AggregateRecordCountRow record(long farmId, String type, long count, long managerCount) {
    AggregateRecordCountRow row = new AggregateRecordCountRow();
    row.setFarmId(farmId);
    row.setRecordType(type);
    row.setRecordCount(count);
    row.setManagerInputCount(managerCount);
    return row;
  }

  private AggregateHarvestRow harvest(
      long farmId,
      String unit,
      String current,
      String previous,
      long currentCount,
      long previousCount) {
    AggregateHarvestRow row = new AggregateHarvestRow();
    row.setFarmId(farmId);
    row.setUnit(unit);
    row.setCurrentQuantity(new BigDecimal(current));
    row.setPreviousQuantity(new BigDecimal(previous));
    row.setCurrentCount(currentCount);
    row.setPreviousCount(previousCount);
    return row;
  }

  private AggregateSalesRow sales(
      long farmId,
      String currentGross,
      String currentFee,
      String currentNet,
      String previousGross,
      String previousNet,
      long currentCount,
      long previousCount) {
    AggregateSalesRow row = new AggregateSalesRow();
    row.setFarmId(farmId);
    row.setCurrentGross(new BigDecimal(currentGross));
    row.setCurrentFee(new BigDecimal(currentFee));
    row.setCurrentNet(new BigDecimal(currentNet));
    row.setPreviousGross(new BigDecimal(previousGross));
    row.setPreviousNet(new BigDecimal(previousNet));
    row.setCurrentCount(currentCount);
    row.setPreviousCount(previousCount);
    return row;
  }

  private AggregateAverageRow average(long farmId, String unit, String quantity, String gross) {
    AggregateAverageRow row = new AggregateAverageRow();
    row.setFarmId(farmId);
    row.setUnit(unit);
    row.setSoldQuantity(new BigDecimal(quantity));
    row.setGrossSalesAmount(new BigDecimal(gross));
    return row;
  }
}
