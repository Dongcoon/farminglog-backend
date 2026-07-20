package com.farmlog.common.tenant;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * {@code farmId} 경로 파라미터를 받는 모든 API가 공통으로 사용해야 하는 농장 접근 검증 유틸
 * (plan/02_Development_Guide.md 7.3 "컨트롤러/서비스마다 개별 구현하지 않는다"). Phase 2에서
 * farm/farm_zone API 구현과 함께 도입하며, Phase 3~9의 모든 농장 스코프 도메인 서비스가 이 클래스를
 * 재사용해야 한다.
 */
@Service
public class FarmAccessGuard {

    private final FarmMembershipMapper farmMembershipMapper;

    public FarmAccessGuard(FarmMembershipMapper farmMembershipMapper) {
        this.farmMembershipMapper = farmMembershipMapper;
    }

    /**
     * 사용자가 해당 농장에 ACTIVE 상태로 소속되어 있는지 검증한다. 비회원이거나, 다른 농장에만
     * 소속되어 있거나, 탈퇴/비활성 상태라면 403({@link ErrorCode#FORBIDDEN})을 던진다 — "농장 A
     * 사용자가 농장 B 데이터를 조회할 수 없다"(9장 Phase 2 DoD)를 강제하는 핵심 로직이다.
     */
    public FarmMembership requireFarmMember(Long userId, Long farmId) {
        String role = farmMembershipMapper.findActiveMemberRole(farmId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "이 농장에 접근할 권한이 없습니다."));
        return new FarmMembership(farmId, userId, role);
    }

    /**
     * 농장 소속 여부와 작업별 역할을 한 번에 검증한다. 조회 권한과 관리 권한을 분리해
     * {@code WORKER}/{@code VIEWER}가 기준정보를 변경하는 일을 서비스마다 빠뜨리지 않도록 한다.
     */
    public FarmMembership requireFarmRole(Long userId, Long farmId, String... allowedRoles) {
        FarmMembership membership = requireFarmMember(userId, farmId);
        if (Arrays.stream(allowedRoles).noneMatch(membership.role()::equals)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 작업을 수행할 농장 권한이 없습니다.");
        }
        return membership;
    }

    /**
     * 부모 farm 잠금 후 멤버 행도 잠금해 대기 중 회수된 권한을 사용하지 않는다.
     * 쓰기 진입점은 비잠금 사전 검사 후 반드시 이 메서드로 재검증한다.
     */
    public FarmMembership requireFarmRoleForUpdate(
            Long userId, Long farmId, String... allowedRoles) {
        String role = farmMembershipMapper.findActiveMemberRoleForUpdate(farmId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FORBIDDEN, "이 농장에 접근할 권한이 없습니다."));
        if (Arrays.stream(allowedRoles).noneMatch(role::equals)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 작업을 수행할 농장 권한이 없습니다.");
        }
        return new FarmMembership(farmId, userId, role);
    }
}
