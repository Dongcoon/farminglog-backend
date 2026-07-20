package com.farmlog.farm.mapper;

import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.entity.FarmMemberEntity;
import com.farmlog.farm.entity.FarmSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@code farm}, {@code farm_member} 테이블 접근 매퍼. {@code organization}/{@code organization_member}는
 * {@link com.farmlog.auth.mapper.AuthMapper}가 단일 소유하므로(Phase 1 컨벤션, 중복 SQL 방지) 이
 * 매퍼에서는 다루지 않는다.
 */
@Mapper
public interface FarmMapper {

    void insert(FarmEntity farm);

    Optional<FarmEntity> findById(@Param("id") Long id);

    Optional<Long> findOrganizationId(@Param("id") Long id);

    Optional<Long> lockActiveOrganization(@Param("organizationId") Long organizationId);

    Optional<FarmEntity> findByIdForUpdate(@Param("id") Long id);

    int incrementStructureVersion(@Param("id") Long id,
                                  @Param("updatedBy") Long updatedBy,
                                  @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /**
     * 로그인 사용자가 ACTIVE 상태로 소속된 모든 농장 + 그 농장에서의 role을 조인해 반환한다
     * ({@code GET /farms}).
     */
    List<FarmSummaryRow> findActiveFarmSummariesForUser(@Param("userId") Long userId);

    void insertMember(FarmMemberEntity member);
}
