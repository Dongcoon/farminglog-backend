package com.farmlog.records.mapper;

import com.farmlog.records.RecordType;
import com.farmlog.records.dto.RecordFilter;
import com.farmlog.records.entity.FeedRow;
import com.farmlog.records.entity.RecordRow;
import com.farmlog.records.entity.ReferenceRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RecordMapper {
  List<RecordRow> findPage(@Param("type") RecordType type, @Param("filter") RecordFilter filter);

  long countPage(@Param("type") RecordType type, @Param("filter") RecordFilter filter);

  Optional<RecordRow> findById(
      @Param("type") RecordType type, @Param("farmId") Long farmId, @Param("id") Long id);

  Optional<Long> lockRecord(
      @Param("type") RecordType type, @Param("farmId") Long farmId, @Param("id") Long id);

  Optional<RecordRow> findByClientRequestId(
      @Param("type") RecordType type,
      @Param("farmId") Long farmId,
      @Param("clientRequestId") String clientRequestId);

  Optional<RecordRow> findByClientRequestIdForUpdate(
      @Param("type") RecordType type,
      @Param("farmId") Long farmId,
      @Param("clientRequestId") String clientRequestId);

  int insertWork(RecordRow row);

  int insertPest(RecordRow row);

  int insertHarvest(RecordRow row);

  int insertSales(RecordRow row);

  int updateWork(@Param("row") RecordRow row, @Param("expectedVersion") Long expectedVersion);

  int updatePest(@Param("row") RecordRow row, @Param("expectedVersion") Long expectedVersion);

  int updateHarvest(@Param("row") RecordRow row, @Param("expectedVersion") Long expectedVersion);

  int updateSales(@Param("row") RecordRow row, @Param("expectedVersion") Long expectedVersion);

  int softDelete(
      @Param("type") RecordType type,
      @Param("farmId") Long farmId,
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("userId") Long userId,
      @Param("now") LocalDateTime now);

  int updateFarmerConfirmation(
      @Param("type") RecordType type,
      @Param("farmId") Long farmId,
      @Param("id") Long id,
      @Param("status") String status,
      @Param("version") Long version,
      @Param("userId") Long userId,
      @Param("now") LocalDateTime now);

  Optional<ReferenceRow> findZone(
      @Param("farmId") Long farmId, @Param("id") Long id, @Param("activeOnly") boolean activeOnly);

  Optional<ReferenceRow> findCurrentZoneForUpdate(
      @Param("farmId") Long farmId, @Param("id") Long id);

  Optional<ReferenceRow> findCrop(
      @Param("farmId") Long farmId, @Param("id") Long id, @Param("activeOnly") boolean activeOnly);

  Optional<ReferenceRow> findVariety(
      @Param("farmId") Long farmId,
      @Param("cropId") Long cropId,
      @Param("id") Long id,
      @Param("activeOnly") boolean activeOnly);

  Optional<ReferenceRow> findSeason(
      @Param("farmId") Long farmId, @Param("id") Long id, @Param("activeOnly") boolean activeOnly);

  Optional<ReferenceRow> findWorkType(
      @Param("farmId") Long farmId, @Param("id") Long id, @Param("activeOnly") boolean activeOnly);

  Optional<ReferenceRow> findCustomer(
      @Param("farmId") Long farmId, @Param("id") Long id, @Param("activeOnly") boolean activeOnly);

  Optional<ReferenceRow> findUser(@Param("id") Long id);

  List<FeedRow> findFeed(
      @Param("farmId") Long farmId,
      @Param("dateFrom") java.time.LocalDate dateFrom,
      @Param("dateTo") java.time.LocalDate dateTo,
      @Param("zoneId") Long zoneId,
      @Param("createdBy") Long createdBy,
      @Param("types") List<RecordType> types,
      @Param("orderBy") String orderBy,
      @Param("direction") String direction,
      @Param("offset") int offset,
      @Param("size") int size);

  long countFeed(
      @Param("farmId") Long farmId,
      @Param("dateFrom") java.time.LocalDate dateFrom,
      @Param("dateTo") java.time.LocalDate dateTo,
      @Param("zoneId") Long zoneId,
      @Param("createdBy") Long createdBy,
      @Param("types") List<RecordType> types);

  List<ReferenceRow> findRecordAuthors(@Param("farmId") Long farmId);

  void insertAudit(
      @Param("organizationId") Long organizationId,
      @Param("farmId") Long farmId,
      @Param("actorUserId") Long actorUserId,
      @Param("action") String action,
      @Param("targetType") String targetType,
      @Param("targetId") Long targetId,
      @Param("detailJson") String detailJson,
      @Param("now") LocalDateTime now);
}
