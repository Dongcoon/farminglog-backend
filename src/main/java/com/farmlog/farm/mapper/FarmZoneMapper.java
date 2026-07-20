package com.farmlog.farm.mapper;

import com.farmlog.farm.entity.FarmZoneAssignmentEntity;
import com.farmlog.farm.entity.FarmZoneEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code farm_zone}, {@code farm_zone_assignment} 테이블 접근 매퍼. 구역 등록 시 두 테이블에
 * 동시에(같은 트랜잭션) 기록해야 하므로 하나의 매퍼로 묶는다(auth 모듈이 organization/refresh_token
 * 등 관련 보조 테이블을 AuthMapper 하나로 묶은 것과 동일한 컨벤션).
 */
@Mapper
public interface FarmZoneMapper {

    void insert(FarmZoneEntity zone);

    Optional<FarmZoneEntity> findById(@Param("id") Long id);

    Optional<FarmZoneEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<Long> findCurrentAssignmentForUpdate(@Param("farmId") Long farmId,
                                                   @Param("zoneId") Long zoneId);

    List<FarmZoneEntity> findByFarmId(@Param("farmId") Long farmId, @Param("includeInactive") boolean includeInactive);

    void update(FarmZoneEntity zone);

    void deactivate(@Param("farmId") Long farmId, @Param("id") Long id,
                    @Param("updatedBy") Long updatedBy, @Param("updatedAt") LocalDateTime updatedAt);

    void insertAssignment(FarmZoneAssignmentEntity assignment);

    List<FarmZoneAssignmentEntity> findAssignmentsByFarmId(@Param("farmId") Long farmId);
}
