package com.farmlog.organizationdashboard.mapper;

import com.farmlog.organizationdashboard.entity.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationDashboardMapper {
  List<Long> findOrganizationFarmIds(@Param("organizationId") Long organizationId);
  long countActiveFarms(@Param("organizationId") Long organizationId);

  List<OrganizationFarmRow> findFarmPage(
      @Param("organizationId") Long organizationId, @Param("q") String q,
      @Param("lifecycleStatus") String lifecycleStatus, @Param("status") String status,
      @Param("dataStatus") String dataStatus, @Param("start") LocalDate start,
      @Param("endExclusive") LocalDate endExclusive, @Param("orderBy") String orderBy,
      @Param("direction") String direction, @Param("offset") int offset, @Param("size") int size);

  long countFarmPage(
      @Param("organizationId") Long organizationId, @Param("q") String q,
      @Param("lifecycleStatus") String lifecycleStatus, @Param("status") String status,
      @Param("dataStatus") String dataStatus, @Param("start") LocalDate start,
      @Param("endExclusive") LocalDate endExclusive);

  Optional<OrganizationFarmRow> findFarmDetail(
      @Param("organizationId") Long organizationId, @Param("farmId") Long farmId);

  List<OrganizationRecordCountRow> findRecordCounts(
      @Param("farmIds") List<Long> farmIds, @Param("start") LocalDate start,
      @Param("endExclusive") LocalDate endExclusive);
  List<OrganizationHarvestRow> findHarvestComparison(
      @Param("farmIds") List<Long> farmIds, @Param("previousStart") LocalDate previousStart,
      @Param("currentStart") LocalDate currentStart, @Param("endExclusive") LocalDate endExclusive);
  List<OrganizationSalesRow> findSalesComparison(
      @Param("farmIds") List<Long> farmIds, @Param("previousStart") LocalDate previousStart,
      @Param("currentStart") LocalDate currentStart, @Param("endExclusive") LocalDate endExclusive);
  List<OrganizationAverageRow> findAverageUnitPrices(
      @Param("farmIds") List<Long> farmIds, @Param("start") LocalDate start,
      @Param("endExclusive") LocalDate endExclusive);

  void insertAudit(@Param("organizationId") Long organizationId, @Param("farmId") Long farmId,
      @Param("actorUserId") Long actorUserId, @Param("action") String action,
      @Param("targetType") String targetType, @Param("targetId") Long targetId,
      @Param("detailJson") String detailJson, @Param("createdAt") LocalDateTime createdAt);
}
