package com.farmlog.report;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMembership;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.entity.FarmMemberEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.report.dto.AggregateMonthlyReportResponse;
import com.farmlog.report.dto.MonthlyReportResponse;
import com.farmlog.report.entity.*;
import com.farmlog.report.mapper.ReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ReportService {
    public static final String RECORDED_STRUCTURE = "RECORDED_STRUCTURE";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Set<String> REPORT_ROLES = Set.of(FarmMemberEntity.ROLE_FARM_OWNER,
            FarmMemberEntity.ROLE_FARM_MANAGER, FarmMemberEntity.ROLE_WORKER, FarmMemberEntity.ROLE_VIEWER);

    private final ReportMapper mapper;
    private final FarmMapper farmMapper;
    private final FarmAccessGuard accessGuard;
    private final ProjectedReportService projected;

    public ReportService(ReportMapper mapper, FarmMapper farmMapper, FarmAccessGuard accessGuard) {
        this(mapper, farmMapper, accessGuard, null);
    }

    @Autowired
    public ReportService(ReportMapper mapper, FarmMapper farmMapper, FarmAccessGuard accessGuard,
                         ProjectedReportService projected) {
        this.mapper = mapper;
        this.farmMapper = farmMapper;
        this.accessGuard = accessGuard;
        this.projected = projected;
    }

    /** 여러 집계 SQL이 서로 다른 시점의 데이터를 보지 않도록 하나의 MVCC 스냅샷에서 계산한다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthlyReportResponse monthly(Long userId, Long farmId, String monthText, String basis) {
        return monthly(userId, farmId, monthText, basis, null);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthlyReportResponse monthly(Long userId, Long farmId, String monthText, String basis, Long eventId) {
        // REPEATABLE_READ의 첫 SELECT보다 먼저 고정해야 no-event CURRENT의 시각이 DB snapshot보다 늦지 않다.
        LocalDateTime structureSnapshotAt = LocalDateTime.now();
        FarmMembership membership = accessGuard.requireFarmMember(userId, farmId);
        if (!REPORT_ROLES.contains(membership.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!Set.of(RECORDED_STRUCTURE, "CURRENT_STRUCTURE", "EVENT").contains(basis))
            throw validation("basis를 확인해주세요.");
        if ("EVENT".equals(basis) != (eventId != null))
            throw validation("eventId는 EVENT basis에서만 필수입니다.");
        YearMonth month;
        try { month = YearMonth.parse(monthText); }
        catch (DateTimeParseException | NullPointerException ex) { throw validation("month는 YYYY-MM 형식이어야 합니다."); }

        if (!RECORDED_STRUCTURE.equals(basis)) {
            if (projected == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            return projected.monthly(userId, farmId, month, basis, eventId, structureSnapshotAt);
        }

        FarmEntity farm = farmMapper.findById(farmId).orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
        var start = month.atDay(1);
        var endExclusive = month.plusMonths(1).atDay(1);
        var previousStart = month.minusMonths(1).atDay(1);

        Map<String, RecordCountRow> counts = new HashMap<>();
        for (RecordCountRow row : mapper.findRecordCounts(farmId, start, endExclusive)) counts.put(row.getRecordType(), row);
        long work = count(counts, "WORK"), pest = count(counts, "PEST_CONTROL");
        long harvest = count(counts, "HARVEST"), sales = count(counts, "SALES");
        long managerCount = counts.values().stream().mapToLong(RecordCountRow::getManagerInputCount).sum();

        List<HarvestComparisonRow> harvestComparison = mapper.findHarvestComparison(farmId, previousStart, start, endExclusive);
        List<MonthlyReportResponse.UnitQuantity> harvestByUnit = harvestComparison.stream()
                .filter(row -> row.getCurrentCount() > 0)
                .map(row -> new MonthlyReportResponse.UnitQuantity(row.getUnit(), quantity(row.getCurrentQuantity()))).toList();
        List<MonthlyReportResponse.HarvestChange> harvestChanges = harvestComparison.stream()
                .map(row -> new MonthlyReportResponse.HarvestChange(row.getUnit(), quantity(row.getCurrentQuantity()),
                        change(row.getCurrentQuantity(), row.getPreviousQuantity(), row.getCurrentCount(), row.getPreviousCount())))
                .toList();

        SalesComparisonRow salesComparison = mapper.findSalesComparison(farmId, previousStart, start, endExclusive);
        if (salesComparison == null) salesComparison = new SalesComparisonRow();
        BigDecimal gross = money(salesComparison.getCurrentGross());
        BigDecimal fee = money(salesComparison.getCurrentFee());
        BigDecimal net = money(salesComparison.getCurrentNet());
        MonthlyReportResponse.Changes changes = new MonthlyReportResponse.Changes(harvestChanges,
                change(salesComparison.getCurrentGross(), salesComparison.getPreviousGross(),
                        salesComparison.getCurrentCount(), salesComparison.getPreviousCount()),
                change(salesComparison.getCurrentNet(), salesComparison.getPreviousNet(),
                        salesComparison.getCurrentCount(), salesComparison.getPreviousCount()));

        List<MonthlyReportResponse.AverageUnitPrice> averages = mapper.findAverageUnitPrices(farmId, start, endExclusive).stream()
                .map(row -> new MonthlyReportResponse.AverageUnitPrice(row.getUnit(), quantity(row.getSoldQuantity()),
                        money(row.getGrossSalesAmount()), money(row.getGrossSalesAmount()).divide(
                        quantity(row.getSoldQuantity()), 2, RoundingMode.HALF_UP))).toList();

        return new MonthlyReportResponse(farmId, farm.getName(), month.toString(), start, endExclusive.minusDays(1),
                RECORDED_STRUCTURE, null, null, null, work + pest + harvest + sales,
                new MonthlyReportResponse.RecordCounts(work, pest, harvest, sales), harvestByUnit,
                gross, fee, net, averages, changes,
                breakdown(mapper.findHarvestByZone(farmId, start, endExclusive)),
                breakdown(mapper.findHarvestByVariety(farmId, start, endExclusive)),
                mapper.findSalesByCustomer(farmId, start, endExclusive).stream().map(this::customer).toList(), managerCount);
    }

    /** 소유 농장 집합과 모든 월 집계를 동일한 MVCC 스냅샷에서 고정해 농장 간 합계가 어긋나지 않게 한다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AggregateMonthlyReportResponse aggregate(Long userId, String monthText, String basis) {
      if (!RECORDED_STRUCTURE.equals(basis)) {
        throw validation("basis는 RECORDED_STRUCTURE만 지원합니다.");
      }
      YearMonth month;
      try {
        month = YearMonth.parse(monthText);
      } catch (DateTimeParseException | NullPointerException ex) {
        throw validation("month는 YYYY-MM 형식이어야 합니다.");
      }
      if (mapper.countOwnerCapabilities(userId) == 0) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
      }

      var farms = mapper.findAggregateFarms(userId);
      var snapshotAt = LocalDateTime.now();
      var start = month.atDay(1);
      var endExclusive = month.plusMonths(1).atDay(1);
      if (farms.isEmpty()) {
        return emptyAggregate(month, start, endExclusive, snapshotAt);
      }

      List<Long> farmIds = farms.stream().map(AggregateFarmRow::getFarmId).toList();
      var records = mapper.findAggregateRecordCounts(farmIds, start, endExclusive);
      var harvests =
          mapper.findAggregateHarvestComparison(
              farmIds, month.minusMonths(1).atDay(1), start, endExclusive);
      var sales =
          mapper.findAggregateSalesComparison(
              farmIds, month.minusMonths(1).atDay(1), start, endExclusive);
      var averages = mapper.findAggregateAverageUnitPrices(farmIds, start, endExclusive);

      Map<Long, FarmAggregate> byFarm = new LinkedHashMap<>();
      farms.forEach(farm -> byFarm.put(farm.getFarmId(), new FarmAggregate(farm)));
      records.forEach(row -> byFarm.get(row.getFarmId()).add(row));
      harvests.forEach(row -> byFarm.get(row.getFarmId()).add(row));
      sales.forEach(row -> byFarm.get(row.getFarmId()).add(row));
      averages.forEach(row -> byFarm.get(row.getFarmId()).add(row));

      List<AggregateMonthlyReportResponse.FarmReport> farmReports =
          byFarm.values().stream().map(FarmAggregate::toResponse).toList();
      long work =
          records.stream()
              .filter(row -> "WORK".equals(row.getRecordType()))
              .mapToLong(AggregateRecordCountRow::getRecordCount)
              .sum();
      long pest =
          records.stream()
              .filter(row -> "PEST_CONTROL".equals(row.getRecordType()))
              .mapToLong(AggregateRecordCountRow::getRecordCount)
              .sum();
      long harvest =
          records.stream()
              .filter(row -> "HARVEST".equals(row.getRecordType()))
              .mapToLong(AggregateRecordCountRow::getRecordCount)
              .sum();
      long sale =
          records.stream()
              .filter(row -> "SALES".equals(row.getRecordType()))
              .mapToLong(AggregateRecordCountRow::getRecordCount)
              .sum();

      Map<String, HarvestTotal> harvestTotals = new TreeMap<>();
      harvests.forEach(
          row ->
              harvestTotals.computeIfAbsent(row.getUnit(), ignored -> new HarvestTotal()).add(row));
      var harvestByUnit =
          harvestTotals.entrySet().stream()
              .filter(entry -> entry.getValue().currentCount > 0)
              .map(
                  entry ->
                      new MonthlyReportResponse.UnitQuantity(
                          entry.getKey(), quantity(entry.getValue().current)))
              .toList();
      var harvestChanges =
          harvestTotals.entrySet().stream()
              .map(
                  entry ->
                      new MonthlyReportResponse.HarvestChange(
                          entry.getKey(),
                          quantity(entry.getValue().current),
                          change(
                              entry.getValue().current,
                              entry.getValue().previous,
                              entry.getValue().currentCount,
                              entry.getValue().previousCount)))
              .toList();

      BigDecimal currentGross = sumSales(sales, AggregateSalesRow::getCurrentGross);
      BigDecimal currentFee = sumSales(sales, AggregateSalesRow::getCurrentFee);
      BigDecimal currentNet = sumSales(sales, AggregateSalesRow::getCurrentNet);
      BigDecimal previousGross = sumSales(sales, AggregateSalesRow::getPreviousGross);
      BigDecimal previousNet = sumSales(sales, AggregateSalesRow::getPreviousNet);
      long currentSalesCount = sales.stream().mapToLong(AggregateSalesRow::getCurrentCount).sum();
      long previousSalesCount = sales.stream().mapToLong(AggregateSalesRow::getPreviousCount).sum();
      var changes =
          new MonthlyReportResponse.Changes(
              harvestChanges,
              change(currentGross, previousGross, currentSalesCount, previousSalesCount),
              change(currentNet, previousNet, currentSalesCount, previousSalesCount));

      Map<String, PriceTotal> priceTotals = new TreeMap<>();
      averages.forEach(
          row -> priceTotals.computeIfAbsent(row.getUnit(), ignored -> new PriceTotal()).add(row));
      var averagePrices =
          priceTotals.entrySet().stream()
              .map(entry -> entry.getValue().toResponse(entry.getKey()))
              .toList();
      long managerCount =
          records.stream().mapToLong(AggregateRecordCountRow::getManagerInputCount).sum();
      long includedCount = work + pest + harvest + sale;

      return new AggregateMonthlyReportResponse(
          month.toString(),
          start,
          endExclusive.minusDays(1),
          RECORDED_STRUCTURE,
          snapshotAt,
          farmReports.size(),
          farmReports.stream().filter(AggregateMonthlyReportResponse.FarmReport::hasData).count(),
          includedCount,
          new MonthlyReportResponse.RecordCounts(work, pest, harvest, sale),
          harvestByUnit,
          money(currentGross),
          money(currentFee),
          money(currentNet),
          averagePrices,
          changes,
          managerCount,
          farmReports);
    }

    private AggregateMonthlyReportResponse emptyAggregate(
        YearMonth month,
        java.time.LocalDate start,
        java.time.LocalDate endExclusive,
        LocalDateTime snapshotAt) {
      var noData = change(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
      return new AggregateMonthlyReportResponse(
          month.toString(),
          start,
          endExclusive.minusDays(1),
          RECORDED_STRUCTURE,
          snapshotAt,
          0,
          0,
          0,
          new MonthlyReportResponse.RecordCounts(0, 0, 0, 0),
          List.of(),
          money(BigDecimal.ZERO),
          money(BigDecimal.ZERO),
          money(BigDecimal.ZERO),
          List.of(),
          new MonthlyReportResponse.Changes(List.of(), noData, noData),
          0,
          List.of());
    }

    private BigDecimal sumSales(
        List<AggregateSalesRow> rows,
        java.util.function.Function<AggregateSalesRow, BigDecimal> extractor) {
      return rows.stream()
          .map(extractor)
          .filter(Objects::nonNull)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private final class FarmAggregate {
      private final AggregateFarmRow farm;
      private final Map<String, AggregateRecordCountRow> records = new HashMap<>();
      private final Map<String, AggregateHarvestRow> harvests = new TreeMap<>();
      private final Map<String, PriceTotal> prices = new TreeMap<>();
      private AggregateSalesRow sales = new AggregateSalesRow();

      private FarmAggregate(AggregateFarmRow farm) {
        this.farm = farm;
      }

      private void add(AggregateRecordCountRow row) {
        records.put(row.getRecordType(), row);
      }

      private void add(AggregateHarvestRow row) {
        harvests.put(row.getUnit(), row);
      }

      private void add(AggregateSalesRow row) {
        sales = row;
      }

      private void add(AggregateAverageRow row) {
        prices.computeIfAbsent(row.getUnit(), ignored -> new PriceTotal()).add(row);
      }

      private AggregateMonthlyReportResponse.FarmReport toResponse() {
        long work = aggregateCount("WORK");
        long pest = aggregateCount("PEST_CONTROL");
        long harvest = aggregateCount("HARVEST");
        long sale = aggregateCount("SALES");
        long included = work + pest + harvest + sale;
        long manager =
            records.values().stream().mapToLong(AggregateRecordCountRow::getManagerInputCount).sum();
        var harvestByUnit =
            harvests.values().stream()
                .filter(row -> row.getCurrentCount() > 0)
                .map(
                    row ->
                        new MonthlyReportResponse.UnitQuantity(
                            row.getUnit(), quantity(row.getCurrentQuantity())))
                .toList();
        return new AggregateMonthlyReportResponse.FarmReport(
            farm.getFarmId(),
            farm.getFarmName(),
            included > 0,
            included,
            new MonthlyReportResponse.RecordCounts(work, pest, harvest, sale),
            harvestByUnit,
            money(sales.getCurrentGross()),
            money(sales.getCurrentFee()),
            money(sales.getCurrentNet()),
            prices.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList(),
            manager);
      }

      private long aggregateCount(String type) {
        return records.containsKey(type) ? records.get(type).getRecordCount() : 0;
      }
    }

    private static final class HarvestTotal {
      private BigDecimal current = BigDecimal.ZERO;
      private BigDecimal previous = BigDecimal.ZERO;
      private long currentCount;
      private long previousCount;

      private void add(AggregateHarvestRow row) {
        current =
            current.add(
                row.getCurrentQuantity() == null ? BigDecimal.ZERO : row.getCurrentQuantity());
        previous =
            previous.add(
                row.getPreviousQuantity() == null ? BigDecimal.ZERO : row.getPreviousQuantity());
        currentCount += row.getCurrentCount();
        previousCount += row.getPreviousCount();
      }
    }

    private final class PriceTotal {
      private BigDecimal quantity = BigDecimal.ZERO;
      private BigDecimal gross = BigDecimal.ZERO;

      private void add(AggregateAverageRow row) {
        quantity =
            quantity.add(row.getSoldQuantity() == null ? BigDecimal.ZERO : row.getSoldQuantity());
        gross =
            gross.add(
                row.getGrossSalesAmount() == null ? BigDecimal.ZERO : row.getGrossSalesAmount());
      }

      private MonthlyReportResponse.AverageUnitPrice toResponse(String unit) {
        return new MonthlyReportResponse.AverageUnitPrice(
            unit,
            quantity(quantity),
            money(gross),
            money(gross).divide(quantity(quantity), 2, RoundingMode.HALF_UP));
      }
    }

    private List<MonthlyReportResponse.HarvestBreakdown> breakdown(List<HarvestBreakdownRow> rows) {
        Map<GroupKey, List<HarvestBreakdownRow>> groups = new LinkedHashMap<>();
        for (HarvestBreakdownRow row : rows) groups.computeIfAbsent(new GroupKey(row.getId(), row.getName()), ignored -> new ArrayList<>()).add(row);
        List<MonthlyReportResponse.HarvestBreakdown> result = new ArrayList<>();
        groups.forEach((key, values) -> result.add(new MonthlyReportResponse.HarvestBreakdown(key.id, key.name,
                values.stream().map(row -> new MonthlyReportResponse.UnitQuantity(row.getUnit(), quantity(row.getQuantity()))).toList(),
                values.stream().mapToLong(HarvestBreakdownRow::getRecordCount).sum(),
                values.stream().mapToLong(HarvestBreakdownRow::getManagerInputCount).sum())));
        return result;
    }

    private MonthlyReportResponse.CustomerBreakdown customer(CustomerBreakdownRow row) {
        return new MonthlyReportResponse.CustomerBreakdown(row.getId(), row.getName(), money(row.getGrossSalesAmount()),
                money(row.getFeeAmount()), money(row.getNetSalesAmount()), row.getRecordCount(), row.getManagerInputCount());
    }

    private MonthlyReportResponse.Change change(BigDecimal currentRaw, BigDecimal previousRaw, long currentCount, long previousCount) {
        BigDecimal current = money(currentRaw), previous = money(previousRaw), absolute = money(current.subtract(previous));
        MonthlyReportResponse.ChangeStatus status;
        BigDecimal rate = null;
        if (currentCount == 0 && previousCount == 0) status = MonthlyReportResponse.ChangeStatus.NO_DATA;
        else if (current.compareTo(BigDecimal.ZERO) == 0 && previous.compareTo(BigDecimal.ZERO) == 0) status = MonthlyReportResponse.ChangeStatus.SAME;
        else if (previous.compareTo(BigDecimal.ZERO) == 0 && current.signum() > 0) status = MonthlyReportResponse.ChangeStatus.NEW;
        else {
            int comparison = current.compareTo(previous);
            status = comparison > 0 ? MonthlyReportResponse.ChangeStatus.UP
                    : comparison < 0 ? MonthlyReportResponse.ChangeStatus.DOWN : MonthlyReportResponse.ChangeStatus.SAME;
            if (previous.compareTo(BigDecimal.ZERO) != 0)
                rate = absolute.multiply(HUNDRED).divide(previous, 2, RoundingMode.HALF_UP);
        }
        return new MonthlyReportResponse.Change(current, previous, absolute, rate, status);
    }

    private long count(Map<String, RecordCountRow> rows, String type) { return rows.containsKey(type) ? rows.get(type).getRecordCount() : 0L; }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal quantity(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private BusinessException validation(String message) { return new BusinessException(ErrorCode.VALIDATION_FAILED, message); }
    private record GroupKey(Long id, String name) {}
}
