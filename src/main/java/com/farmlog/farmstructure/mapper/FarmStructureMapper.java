package com.farmlog.farmstructure.mapper;

import com.farmlog.farmstructure.entity.StructureEventRow;
import com.farmlog.farmstructure.entity.StructureFarmRow;
import com.farmlog.farmstructure.entity.StructureItemRow;
import com.farmlog.farmstructure.entity.ProjectedRecordRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface FarmStructureMapper {
  boolean isSystemAdmin(@Param("userId") Long userId);
  boolean isOrgAdmin(@Param("userId") Long userId, @Param("organizationId") Long organizationId);
  long countOwnedRelated(@Param("userId") Long userId, @Param("farmIds") List<Long> farmIds);
  Optional<Long> lockOrganization(@Param("organizationId") Long organizationId);
  List<StructureFarmRow> findFarmsByIds(@Param("farmIds") List<Long> farmIds);
  List<StructureFarmRow> lockFarms(@Param("farmIds") List<Long> farmIds);

  List<Map<String,Object>> findContextOrganizations(@Param("userId") Long userId,
      @Param("organizationId") Long organizationId, @Param("q") String q,
      @Param("system") boolean system, @Param("offset") int offset, @Param("size") int size);
  long countContextOrganizations(@Param("userId") Long userId, @Param("organizationId") Long organizationId,
      @Param("q") String q, @Param("system") boolean system);
  List<StructureFarmRow> findContextFarms(@Param("userId") Long userId, @Param("organizationId") Long organizationId,
      @Param("q") String q, @Param("admin") boolean admin, @Param("offset") int offset, @Param("size") int size);
  long countContextFarms(@Param("userId") Long userId, @Param("organizationId") Long organizationId,
      @Param("q") String q, @Param("admin") boolean admin);
  Optional<StructureFarmRow> findContextFarm(@Param("userId") Long userId, @Param("farmId") Long farmId,
      @Param("admin") boolean admin);
  List<Map<String,Object>> findOwners(@Param("farmId") Long farmId);
  List<Map<String,Object>> findActiveMembers(@Param("farmId") Long farmId);
  List<Map<String,Object>> findCurrentZones(@Param("farmId") Long farmId);
  Map<String,Object> findMasterSummary(@Param("farmId") Long farmId);
  List<Map<String,Object>> findMasterResources(@Param("farmIds") List<Long> farmIds,
      @Param("scopes") List<String> scopes);
  int copyMasterResource(@Param("resourceType") String resourceType,
      @Param("resourceName") String resourceName, @Param("sourceFarmId") Long sourceFarmId,
      @Param("sourceResourceId") Long sourceResourceId, @Param("targetFarmId") Long targetFarmId,
      @Param("organizationId") Long organizationId, @Param("targetName") String targetName,
      @Param("targetResourceId") Long targetResourceId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int insertCropCatalogIfAbsent(@Param("name") String name);
  Optional<Long> findCropCatalogId(@Param("name") String name);
  int insertVarietyCatalogIfAbsent(@Param("cropId") Long cropId, @Param("name") String name);
  Optional<Long> findVarietyCatalogId(@Param("cropId") Long cropId, @Param("name") String name);
  int reactivateMasterResource(@Param("resourceType") String resourceType,
      @Param("resourceName") String resourceName, @Param("farmId") Long farmId,
      @Param("resourceId") Long resourceId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int deactivateMasterResource(@Param("resourceType") String resourceType,
      @Param("resourceName") String resourceName, @Param("farmId") Long farmId,
      @Param("resourceId") Long resourceId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);

  Optional<StructureEventRow> findByPreviewRequest(@Param("userId") Long userId, @Param("requestId") String requestId);
  Optional<StructureEventRow> findByConfirmRequest(@Param("userId") Long userId, @Param("requestId") String requestId);
  Optional<StructureEventRow> findEvent(@Param("id") Long id);
  Optional<StructureEventRow> lockEvent(@Param("id") Long id);
  Optional<StructureEventRow> findLastAppliedEvent(@Param("organizationId") Long organizationId);
  List<StructureEventRow> findEvents(@Param("userId") Long userId, @Param("organizationId") Long organizationId,
      @Param("admin") boolean admin, @Param("eventType") String eventType, @Param("status") String status,
      @Param("sourceFarmId") Long sourceFarmId, @Param("targetFarmId") Long targetFarmId,
      @Param("effectiveFrom") LocalDate effectiveFrom, @Param("effectiveToExclusive") LocalDate effectiveToExclusive,
      @Param("now") LocalDateTime now, @Param("offset") int offset, @Param("size") int size);
  long countEvents(@Param("userId") Long userId, @Param("organizationId") Long organizationId,
      @Param("admin") boolean admin, @Param("eventType") String eventType, @Param("status") String status,
      @Param("sourceFarmId") Long sourceFarmId, @Param("targetFarmId") Long targetFarmId,
      @Param("effectiveFrom") LocalDate effectiveFrom, @Param("effectiveToExclusive") LocalDate effectiveToExclusive,
      @Param("now") LocalDateTime now);
  void insertEvent(StructureEventRow row);
  int confirmEvent(@Param("id") Long id, @Param("version") Long version,
      @Param("confirmRequestId") String confirmRequestId, @Param("confirmRequestHash") String confirmRequestHash,
      @Param("confirmedStateHash") String confirmedStateHash, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int claimConfirmRequest(@Param("id") Long id, @Param("version") Long version,
      @Param("confirmRequestId") String confirmRequestId,
      @Param("confirmRequestHash") String confirmRequestHash, @Param("userId") Long userId);
  int cancelOriginal(@Param("id") Long id, @Param("version") Long version, @Param("cancelEventId") Long cancelEventId,
      @Param("userId") Long userId, @Param("now") LocalDateTime now);
  void insertItem(StructureItemRow row);
  List<StructureItemRow> findItems(@Param("eventId") Long eventId);
  List<StructureItemRow> findProjectionItems(@Param("organizationId") Long organizationId,
      @Param("cutoffAt") LocalDateTime cutoffAt, @Param("cutoffId") Long cutoffId, @Param("current") boolean current);
  List<ProjectedRecordRow> findProjectionRecords(@Param("organizationId") Long organizationId,
      @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

  List<Map<String,Object>> lockCurrentZones(@Param("zoneIds") List<Long> zoneIds);
  List<Map<String,Object>> findCurrentZonesByIds(@Param("zoneIds") List<Long> zoneIds);
  List<Long> findCurrentZoneIds(@Param("farmIds") List<Long> farmIds);
  int moveZone(@Param("zoneId") Long zoneId, @Param("fromFarmId") Long fromFarmId,
      @Param("toFarmId") Long toFarmId, @Param("version") Long version, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int closeAssignment(@Param("id") Long id, @Param("version") Long version, @Param("effectiveTo") LocalDate effectiveTo,
      @Param("eventId") Long eventId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
  int moveAssignmentInPlace(@Param("id") Long id, @Param("version") Long version, @Param("farmId") Long farmId,
      @Param("eventId") Long eventId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
  int updateLifecycle(@Param("farmId") Long farmId, @Param("lifecycle") String lifecycle,
      @Param("userId") Long userId, @Param("now") LocalDateTime now);
  long countCurrentZones(@Param("farmId") Long farmId);
  long countZoneLessSales(@Param("farmId") Long farmId);
  Map<String,Object> countImpact(@Param("farmIds") List<Long> farmIds);
  long countActiveMembers(@Param("farmIds") List<Long> farmIds);
  long countActiveMembersNotInTarget(@Param("farmIds") List<Long> farmIds,
      @Param("targetFarmId") Long targetFarmId);
  List<Long> findActiveMemberIdsNotInTarget(@Param("farmIds") List<Long> farmIds,
      @Param("targetFarmId") Long targetFarmId);
  void copyMembersToNewTarget(@Param("farmIds") List<Long> sourceFarmIds, @Param("targetFarmId") Long targetFarmId,
      @Param("ownerUserId") Long ownerUserId, @Param("now") LocalDateTime now);
  int copyFarmCrops(@Param("farmIds") List<Long> sourceFarmIds, @Param("targetFarmId") Long targetFarmId,
      @Param("userId") Long userId, @Param("now") LocalDateTime now);
  int copyFarmVarieties(@Param("farmIds") List<Long> sourceFarmIds, @Param("targetFarmId") Long targetFarmId,
      @Param("userId") Long userId, @Param("now") LocalDateTime now);
  int copySeasons(@Param("farmIds") List<Long> sourceFarmIds, @Param("targetFarmId") Long targetFarmId,
      @Param("organizationId") Long organizationId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
  int copyWorkTypes(@Param("farmIds") List<Long> sourceFarmIds, @Param("targetFarmId") Long targetFarmId,
      @Param("organizationId") Long organizationId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
  int copyCustomers(@Param("farmIds") List<Long> sourceFarmIds, @Param("targetFarmId") Long targetFarmId,
      @Param("organizationId") Long organizationId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
  int copyMaterials(@Param("farmIds") List<Long> sourceFarmIds, @Param("targetFarmId") Long targetFarmId,
      @Param("organizationId") Long organizationId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
  long countBlockingDependents(@Param("farmId") Long farmId);
  long countNewerRelatedEvents(@Param("eventId") Long eventId,
      @Param("confirmedAt") LocalDateTime confirmedAt,
      @Param("farmIds") List<Long> farmIds, @Param("zoneIds") List<Long> zoneIds);
  int deactivateTargetMembers(@Param("farmId") Long farmId, @Param("now") LocalDateTime now);
  int deactivateTargetCrops(@Param("farmId") Long farmId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int deactivateTargetVarieties(@Param("farmId") Long farmId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int deactivateTargetSeasons(@Param("farmId") Long farmId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int deactivateTargetWorkTypes(@Param("farmId") Long farmId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int deactivateTargetCustomers(@Param("farmId") Long farmId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  int deactivateTargetMaterials(@Param("farmId") Long farmId, @Param("userId") Long userId,
      @Param("now") LocalDateTime now);
  void insertAudit(@Param("organizationId") Long organizationId, @Param("farmId") Long farmId,
      @Param("userId") Long userId, @Param("action") String action, @Param("targetId") Long targetId,
      @Param("detail") String detail, @Param("now") LocalDateTime now);
}
