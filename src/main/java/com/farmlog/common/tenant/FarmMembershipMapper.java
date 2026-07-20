package com.farmlog.common.tenant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * {@code farm_member} 테이블에 대한 테넌트 검증 전용 조회. {@code farm} 모듈의
 * {@code com.farmlog.farm.mapper.FarmMapper}와 일부러 분리한다 — plan/02_Development_Guide.md 7.3
 * "모든 API는 서비스 계층에서 organization_id, farm_id 소유권을 공통 검증 유틸(common/tenant)로
 * 강제한다. 컨트롤러/서비스마다 개별 구현하지 않는다"에 따라, Phase 3~9의 모든 {@code farmId} 기반
 * 도메인 모듈이 {@code farm} 모듈에 직접 의존하지 않고도 이 검증 하나만 재사용할 수 있어야 하기 때문이다.
 */
@Mapper
public interface FarmMembershipMapper {

    Optional<String> findActiveMemberRole(@Param("farmId") Long farmId, @Param("userId") Long userId);

    Optional<String> findActiveMemberRoleForUpdate(@Param("farmId") Long farmId, @Param("userId") Long userId);
}
