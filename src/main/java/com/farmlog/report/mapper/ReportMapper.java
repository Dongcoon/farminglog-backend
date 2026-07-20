package com.farmlog.report.mapper;

import com.farmlog.report.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReportMapper {
    List<RecordCountRow> findRecordCounts(@Param("farmId") Long farmId, @Param("start") LocalDate start,
                                          @Param("endExclusive") LocalDate endExclusive);
    List<HarvestComparisonRow> findHarvestComparison(@Param("farmId") Long farmId,
            @Param("previousStart") LocalDate previousStart, @Param("currentStart") LocalDate currentStart,
            @Param("endExclusive") LocalDate endExclusive);
    SalesComparisonRow findSalesComparison(@Param("farmId") Long farmId,
            @Param("previousStart") LocalDate previousStart, @Param("currentStart") LocalDate currentStart,
            @Param("endExclusive") LocalDate endExclusive);
    List<AveragePriceRow> findAverageUnitPrices(@Param("farmId") Long farmId, @Param("start") LocalDate start,
                                                @Param("endExclusive") LocalDate endExclusive);
    List<HarvestBreakdownRow> findHarvestByZone(@Param("farmId") Long farmId, @Param("start") LocalDate start,
                                                @Param("endExclusive") LocalDate endExclusive);
    List<HarvestBreakdownRow> findHarvestByVariety(@Param("farmId") Long farmId, @Param("start") LocalDate start,
                                                   @Param("endExclusive") LocalDate endExclusive);
    List<CustomerBreakdownRow> findSalesByCustomer(@Param("farmId") Long farmId, @Param("start") LocalDate start,
                                                   @Param("endExclusive") LocalDate endExclusive);
    long countOwnerCapabilities(@Param("userId") Long userId);

    List<AggregateFarmRow> findAggregateFarms(@Param("userId") Long userId);

    List<AggregateRecordCountRow> findAggregateRecordCounts(
        @Param("farmIds") List<Long> farmIds,
        @Param("start") LocalDate start,
        @Param("endExclusive") LocalDate endExclusive);

    List<AggregateHarvestRow> findAggregateHarvestComparison(
        @Param("farmIds") List<Long> farmIds,
        @Param("previousStart") LocalDate previousStart,
        @Param("currentStart") LocalDate currentStart,
        @Param("endExclusive") LocalDate endExclusive);

    List<AggregateSalesRow> findAggregateSalesComparison(
        @Param("farmIds") List<Long> farmIds,
        @Param("previousStart") LocalDate previousStart,
        @Param("currentStart") LocalDate currentStart,
        @Param("endExclusive") LocalDate endExclusive);

    List<AggregateAverageRow> findAggregateAverageUnitPrices(
        @Param("farmIds") List<Long> farmIds,
        @Param("start") LocalDate start,
        @Param("endExclusive") LocalDate endExclusive);
}
