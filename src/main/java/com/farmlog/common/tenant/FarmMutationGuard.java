package com.farmlog.common.tenant;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 농장 범위 쓰기의 공통 직렬화 지점이다. 권한을 먼저 확인한 뒤 이 guard로 부모 농장을
 * 잠그고, 그 다음에만 구역·기록·배정 같은 자식 행을 읽거나 잠근다.
 */
@Service
public class FarmMutationGuard {

    private final FarmMapper farmMapper;

    public FarmMutationGuard(FarmMapper farmMapper) {
        this.farmMapper = farmMapper;
    }

    public FarmEntity lockActiveFarm(Long farmId) {
        FarmEntity farm = farmMapper.findByIdForUpdate(farmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
        if (!FarmEntity.STATUS_ACTIVE.equals(farm.getStatus())
                || !FarmEntity.LIFECYCLE_ACTIVE.equals(farm.getLifecycleStatus())
                || farm.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FARM_READ_ONLY);
        }
        return farm;
    }

    /**
     * 구역·기준정보·멤버십처럼 구조 버전에 영향을 주는 쓰기의 전역 mutex다.
     * 비잠금 농장 탐색은 organization id를 알아내기 위함이고, 실제 검증은
     * organization -&gt; farm 순서의 current/locking read로 다시 수행한다.
     */
    public FarmEntity lockActiveStructureFarm(Long farmId) {
        Long organizationId = farmMapper.findOrganizationId(farmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
        farmMapper.lockActiveOrganization(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
        return lockActiveFarm(farmId);
    }

    /** 농장 생성은 farm 행이 아직 없으므로 organization mutex만 선점한다. */
    public void lockActiveOrganization(Long organizationId) {
        farmMapper.lockActiveOrganization(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
    }

    /** 구조에 영향을 주는 명령은 부모 잠금을 보유한 상태에서 버전을 한 번만 증가시킨다. */
    public void incrementStructureVersion(Long farmId, Long userId) {
        if (farmMapper.incrementStructureVersion(farmId, userId, LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }
}
