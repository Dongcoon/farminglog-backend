package com.farmlog.report;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMembership;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.report.entity.*;
import com.farmlog.report.mapper.ReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private ReportMapper mapper;
    private FarmAccessGuard guard;
    private ReportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ReportMapper.class);
        FarmMapper farmMapper = mock(FarmMapper.class);
        guard = mock(FarmAccessGuard.class);
        service = new ReportService(mapper, farmMapper, guard);
        when(guard.requireFarmMember(7L, 11L)).thenReturn(new FarmMembership(11L, 7L, "VIEWER"));
        when(farmMapper.findById(11L)).thenReturn(Optional.of(FarmEntity.builder().id(11L).name("테스트 농장").build()));
        when(mapper.findRecordCounts(anyLong(), any(), any())).thenReturn(List.of());
        when(mapper.findAverageUnitPrices(anyLong(), any(), any())).thenReturn(List.of());
        when(mapper.findHarvestByZone(anyLong(), any(), any())).thenReturn(List.of());
        when(mapper.findHarvestByVariety(anyLong(), any(), any())).thenReturn(List.of());
        when(mapper.findSalesByCustomer(anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    void keepsDifferentUnitsSeparateAndCalculatesPreviousMonthChanges() {
        HarvestComparisonRow kg = harvest("kg", "10", "4", 1, 1);
        HarvestComparisonRow box = harvest("box", "2", "0", 1, 0);
        when(mapper.findHarvestComparison(anyLong(), any(), any(), any())).thenReturn(List.of(box, kg));
        SalesComparisonRow sales = new SalesComparisonRow();
        sales.setCurrentGross(new BigDecimal("150")); sales.setPreviousGross(new BigDecimal("100"));
        sales.setCurrentNet(new BigDecimal("120")); sales.setPreviousNet(new BigDecimal("100"));
        sales.setCurrentCount(1); sales.setPreviousCount(1);
        when(mapper.findSalesComparison(anyLong(), any(), any(), any())).thenReturn(sales);

        var response = service.monthly(7L, 11L, "2026-07", "RECORDED_STRUCTURE");

        assertThat(response.harvestByUnit()).extracting(item -> item.unit()).containsExactly("box", "kg");
        assertThat(response.changes().harvestByUnit().get(0).change().status().name()).isEqualTo("NEW");
        assertThat(response.changes().grossSales().ratePercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void careManagerCannotReadMonthlyReport() {
        when(guard.requireFarmMember(7L, 11L)).thenReturn(new FarmMembership(11L, 7L, "FARM_CARE_MANAGER"));
        assertThatThrownBy(() -> service.monthly(7L, 11L, "2026-07", "RECORDED_STRUCTURE"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void rejectsUnsupportedBasisAndMalformedMonth() {
        assertThatThrownBy(() -> service.monthly(7L, 11L, "2026/07", "RECORDED_STRUCTURE"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.monthly(7L, 11L, "2026-07", "UNKNOWN"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.monthly(7L, 11L, "2026-07", "EVENT", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void workAndPestRecordsMakeIncludedCountNonZeroWithoutSalesOrHarvest() {
        when(mapper.findRecordCounts(anyLong(), any(), any())).thenReturn(List.of(count("WORK", 2, 1), count("PEST_CONTROL", 3, 2)));
        when(mapper.findHarvestComparison(anyLong(), any(), any(), any())).thenReturn(List.of());

        var response = service.monthly(7L, 11L, "2026-07", "RECORDED_STRUCTURE");

        assertThat(response.includedRecordCount()).isEqualTo(5);
        assertThat(response.recordCounts().work()).isEqualTo(2);
        assertThat(response.recordCounts().pestControl()).isEqualTo(3);
        assertThat(response.managerInputCount()).isEqualTo(3);
    }

    @Test
    void previousOnlyUnitHasZeroCurrentAndMinusHundredPercent() {
        when(mapper.findHarvestComparison(anyLong(), any(), any(), any()))
                .thenReturn(List.of(harvest("kg", "0", "7", 0, 1)));

        var response = service.monthly(7L, 11L, "2026-07", "RECORDED_STRUCTURE");

        assertThat(response.harvestByUnit()).isEmpty();
        var change = response.changes().harvestByUnit().get(0).change();
        assertThat(change.current()).isEqualByComparingTo("0.00");
        assertThat(change.ratePercent()).isEqualByComparingTo("-100.00");
        assertThat(change.status().name()).isEqualTo("DOWN");
    }

    @Test
    void salesChangesDistinguishNewNoDataAndSameZeroAmountRecord() {
        when(mapper.findHarvestComparison(anyLong(), any(), any(), any())).thenReturn(List.of());
        SalesComparisonRow fresh = sales("10", "0", 1, 0);
        when(mapper.findSalesComparison(anyLong(), any(), any(), any())).thenReturn(fresh, null, sales("0", "0", 1, 1));

        assertThat(service.monthly(7L, 11L, "2026-07", "RECORDED_STRUCTURE").changes().grossSales().status().name())
                .isEqualTo("NEW");
        assertThat(service.monthly(7L, 11L, "2026-07", "RECORDED_STRUCTURE").changes().grossSales().status().name())
                .isEqualTo("NO_DATA");
        assertThat(service.monthly(7L, 11L, "2026-07", "RECORDED_STRUCTURE").changes().grossSales().status().name())
                .isEqualTo("SAME");
    }

    @Test
    void averagePriceRoundsHalfUpAndBreakdownSumsManagerCountsAcrossUnits() {
        when(mapper.findHarvestComparison(anyLong(), any(), any(), any())).thenReturn(List.of());
        AveragePriceRow average = new AveragePriceRow();
        average.setUnit("kg"); average.setSoldQuantity(new BigDecimal("3")); average.setGrossSalesAmount(new BigDecimal("10"));
        when(mapper.findAverageUnitPrices(anyLong(), any(), any())).thenReturn(List.of(average));
        HarvestBreakdownRow kg = breakdown(4L, "A구역", "kg", "5", 2, 1);
        HarvestBreakdownRow box = breakdown(4L, "A구역", "box", "1", 1, 1);
        when(mapper.findHarvestByZone(anyLong(), any(), any())).thenReturn(List.of(box, kg));

        var response = service.monthly(7L, 11L, "2026-07", "RECORDED_STRUCTURE");

        assertThat(response.averageUnitPrices().get(0).averageUnitPrice()).isEqualByComparingTo("3.33");
        assertThat(response.byZone()).hasSize(1);
        assertThat(response.byZone().get(0).recordCount()).isEqualTo(3);
        assertThat(response.byZone().get(0).managerInputCount()).isEqualTo(2);
        assertThat(response.byZone().get(0).harvestByUnit()).extracting(item -> item.unit()).containsExactly("box", "kg");
    }

    private HarvestComparisonRow harvest(String unit, String current, String previous, long currentCount, long previousCount) {
        HarvestComparisonRow row = new HarvestComparisonRow();
        row.setUnit(unit); row.setCurrentQuantity(new BigDecimal(current)); row.setPreviousQuantity(new BigDecimal(previous));
        row.setCurrentCount(currentCount); row.setPreviousCount(previousCount);
        return row;
    }

    private RecordCountRow count(String type, long total, long manager) {
        RecordCountRow row = new RecordCountRow();
        row.setRecordType(type); row.setRecordCount(total); row.setManagerInputCount(manager);
        return row;
    }

    private SalesComparisonRow sales(String current, String previous, long currentCount, long previousCount) {
        SalesComparisonRow row = new SalesComparisonRow();
        row.setCurrentGross(new BigDecimal(current)); row.setPreviousGross(new BigDecimal(previous));
        row.setCurrentNet(new BigDecimal(current)); row.setPreviousNet(new BigDecimal(previous));
        row.setCurrentCount(currentCount); row.setPreviousCount(previousCount);
        return row;
    }

    private HarvestBreakdownRow breakdown(long id, String name, String unit, String quantity, long count, long manager) {
        HarvestBreakdownRow row = new HarvestBreakdownRow();
        row.setId(id); row.setName(name); row.setUnit(unit); row.setQuantity(new BigDecimal(quantity));
        row.setRecordCount(count); row.setManagerInputCount(manager);
        return row;
    }
}
