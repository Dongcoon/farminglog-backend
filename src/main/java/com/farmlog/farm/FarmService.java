package com.farmlog.farm;

import com.farmlog.auth.entity.OrganizationMemberEntity;
import com.farmlog.auth.mapper.AuthMapper;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.farm.dto.FarmCreateRequest;
import com.farmlog.farm.dto.FarmResponse;
import com.farmlog.farm.dto.FarmZoneAssignmentResponse;
import com.farmlog.farm.dto.FarmZoneCreateRequest;
import com.farmlog.farm.dto.FarmZoneResponse;
import com.farmlog.farm.dto.FarmZoneUpdateRequest;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.entity.FarmMemberEntity;
import com.farmlog.farm.entity.FarmSummaryRow;
import com.farmlog.farm.entity.FarmZoneAssignmentEntity;
import com.farmlog.farm.entity.FarmZoneEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farm.mapper.FarmZoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 농장/구역(하우스) 설정(Phase 2, M-04/M-14/W-04) 비즈니스 로직.
 *
 * <p>{@code farmId}를 받는 모든 메서드는 {@link FarmAccessGuard#requireFarmMember(Long, Long)}를 가장
 * 먼저 호출해 소속을 검증한다(plan/02_Development_Guide.md 7.3 공통 규칙 — "모든 API는 서비스 계층에서
 * organization_id, farm_id 소유권을 공통 검증 유틸로 강제한다"). Phase 3 이후 모든 농장 스코프 도메인
 * 서비스도 이 패턴을 그대로 따라야 한다.</p>
 */
@Service
public class FarmService {

    private final FarmMapper farmMapper;
    private final FarmZoneMapper farmZoneMapper;
    private final AuthMapper authMapper;
    private final FarmAccessGuard farmAccessGuard;
    private final FarmMutationGuard farmMutationGuard;

    public FarmService(FarmMapper farmMapper, FarmZoneMapper farmZoneMapper, AuthMapper authMapper,
                        FarmAccessGuard farmAccessGuard, FarmMutationGuard farmMutationGuard) {
        this.farmMapper = farmMapper;
        this.farmZoneMapper = farmZoneMapper;
        this.authMapper = authMapper;
        this.farmAccessGuard = farmAccessGuard;
        this.farmMutationGuard = farmMutationGuard;
    }

    public List<FarmResponse> listMyFarms(Long userId) {
        return farmMapper.findActiveFarmSummariesForUser(userId).stream()
                .map(this::toFarmResponse)
                .toList();
    }

    @Transactional
    public FarmResponse createFarm(Long userId, FarmCreateRequest request) {
        // organization_id/owner는 클라이언트가 넘기지 않고, 가입 시 자동 생성된 본인의 개인(PERSONAL)
        // 조직 멤버십에서 서버가 직접 조회한다(7.3, Phase 1 AuthService.signUp 참고).
        OrganizationMemberEntity membership = authMapper.findActiveOrganizationMembershipByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
        farmMutationGuard.lockActiveOrganization(membership.getOrganizationId());
        membership = authMapper.findActiveOrganizationMembershipForUpdate(
                        membership.getOrganizationId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        FarmEntity farm = FarmEntity.builder()
                .organizationId(membership.getOrganizationId())
                .name(request.name())
                .ownerUserId(userId)
                .address(request.address())
                .lifecycleStatus(FarmEntity.LIFECYCLE_ACTIVE)
                .status(FarmEntity.STATUS_ACTIVE)
                .createdBy(userId)
                .createdAt(now)
                .build();
        farmMapper.insert(farm);

        FarmMemberEntity member = FarmMemberEntity.builder()
                .farmId(farm.getId())
                .userId(userId)
                .role(FarmMemberEntity.ROLE_FARM_OWNER)
                .status(FarmMemberEntity.STATUS_ACTIVE)
                .createdAt(now)
                .build();
        farmMapper.insertMember(member);

        return new FarmResponse(farm.getId(), farm.getName(), farm.getFarmCode(), farm.getAddress(),
                farm.getLifecycleStatus(), farm.getStatus(), FarmMemberEntity.ROLE_FARM_OWNER);
    }

    public List<FarmZoneResponse> listZones(Long userId, Long farmId, boolean includeInactive) {
        farmAccessGuard.requireFarmMember(userId, farmId);
        return farmZoneMapper.findByFarmId(farmId, includeInactive).stream()
                .map(this::toZoneResponse)
                .toList();
    }

    @Transactional
    public FarmZoneResponse createZone(Long userId, Long farmId, FarmZoneCreateRequest request) {
        requireZoneManager(userId, farmId);
        FarmEntity farm = farmMutationGuard.lockActiveStructureFarm(farmId);
        requireZoneManagerForUpdate(userId, farmId);

        LocalDateTime now = LocalDateTime.now();
        FarmZoneEntity zone = FarmZoneEntity.builder()
                .organizationId(farm.getOrganizationId())
                .farmId(farmId)
                .name(request.name())
                .zoneType(StringUtils.hasText(request.zoneType()) ? request.zoneType() : FarmZoneEntity.DEFAULT_ZONE_TYPE)
                .areaValue(request.areaValue())
                .areaUnit(request.areaUnit())
                .displayOrder(0)
                .activeYn(FarmZoneEntity.ACTIVE_YES)
                .createdBy(userId)
                .createdAt(now)
                .build();
        farmZoneMapper.insert(zone);

        // 구역 등록 시 같은 트랜잭션에서 소속 이력을 자동 생성한다
        // (9장 Phase 2 DoD "구역 등록 시 farm_zone_assignment 이력 자동 생성 확인" — 절대 생략하지 않음).
        FarmZoneAssignmentEntity assignment = FarmZoneAssignmentEntity.builder()
                .organizationId(farm.getOrganizationId())
                .zoneId(zone.getId())
                .farmId(farmId)
                .effectiveFrom(LocalDate.now())
                .effectiveTo(null)
                .changeEventId(null)
                .activeYn(FarmZoneAssignmentEntity.ACTIVE_YES)
                .createdBy(userId)
                .createdAt(now)
                .build();
        farmZoneMapper.insertAssignment(assignment);
        farmMutationGuard.incrementStructureVersion(farmId, userId);

        return toZoneResponse(zone);
    }

    @Transactional
    public FarmZoneResponse updateZone(Long userId, Long farmId, Long zoneId, FarmZoneUpdateRequest request) {
        requireZoneManager(userId, farmId);
        farmMutationGuard.lockActiveStructureFarm(farmId);
        requireZoneManagerForUpdate(userId, farmId);
        FarmZoneEntity zone = getZoneForUpdateOrThrow(farmId, zoneId);
        requireCurrentAssignment(farmId, zoneId);

        boolean changed = (request.name() != null && !request.name().equals(zone.getName()))
                || (request.zoneType() != null && !request.zoneType().equals(zone.getZoneType()))
                || (request.hasAreaValue() && !java.util.Objects.equals(request.areaValue(), zone.getAreaValue()))
                || (request.hasAreaUnit() && !java.util.Objects.equals(request.areaUnit(), zone.getAreaUnit()))
                || (request.displayOrder() != null && !request.displayOrder().equals(zone.getDisplayOrder()));
        if (!changed) return toZoneResponse(zone);

        if (request.name() != null) {
            zone.setName(request.name());
        }
        if (request.zoneType() != null) {
            zone.setZoneType(request.zoneType());
        }
        if (request.hasAreaValue()) {
            zone.setAreaValue(request.areaValue());
        }
        if (request.hasAreaUnit()) {
            zone.setAreaUnit(request.areaUnit());
        }
        if (request.displayOrder() != null) {
            zone.setDisplayOrder(request.displayOrder());
        }
        zone.setUpdatedBy(userId);
        zone.setUpdatedAt(LocalDateTime.now());
        farmZoneMapper.update(zone);
        farmMutationGuard.incrementStructureVersion(farmId, userId);

        return toZoneResponse(zone);
    }

    @Transactional
    public FarmZoneResponse deactivateZone(Long userId, Long farmId, Long zoneId) {
        requireZoneManager(userId, farmId);
        farmMutationGuard.lockActiveStructureFarm(farmId);
        requireZoneManagerForUpdate(userId, farmId);
        FarmZoneEntity zone = getZoneForUpdateOrThrow(farmId, zoneId);
        requireCurrentAssignment(farmId, zoneId);
        if (FarmZoneEntity.ACTIVE_NO.equals(zone.getActiveYn())) return toZoneResponse(zone);

        // 소프트 비활성화만 수행한다 — 하드 삭제도, farm_zone_assignment 이력 변경도 하지 않는다
        // (요구사항: "Does NOT touch farm_zone_assignment history").
        LocalDateTime now = LocalDateTime.now();
        farmZoneMapper.deactivate(farmId, zoneId, userId, now);
        farmMutationGuard.incrementStructureVersion(farmId, userId);

        zone.setActiveYn(FarmZoneEntity.ACTIVE_NO);
        zone.setUpdatedBy(userId);
        zone.setUpdatedAt(now);
        return toZoneResponse(zone);
    }

    public List<FarmZoneAssignmentResponse> listZoneAssignments(Long userId, Long farmId) {
        farmAccessGuard.requireFarmMember(userId, farmId);
        return farmZoneMapper.findAssignmentsByFarmId(farmId).stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    private FarmEntity getFarmOrThrow(Long farmId) {
        return farmMapper.findById(farmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    }

    private void requireZoneManager(Long userId, Long farmId) {
        // W-04 기준정보 변경은 농장주와 농장 매니저만 가능하다. 작업자/조회자는 목록만 조회한다.
        farmAccessGuard.requireFarmRole(userId, farmId,
                FarmMemberEntity.ROLE_FARM_OWNER, FarmMemberEntity.ROLE_FARM_MANAGER);
    }

    private void requireZoneManagerForUpdate(Long userId, Long farmId) {
        farmAccessGuard.requireFarmRoleForUpdate(userId, farmId,
                FarmMemberEntity.ROLE_FARM_OWNER, FarmMemberEntity.ROLE_FARM_MANAGER);
    }

    private void requireCurrentAssignment(Long farmId, Long zoneId) {
        farmZoneMapper.findCurrentAssignmentForUpdate(farmId, zoneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARM_ZONE_NOT_FOUND));
    }

    private FarmZoneEntity getZoneOrThrow(Long farmId, Long zoneId) {
        FarmZoneEntity zone = farmZoneMapper.findById(zoneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARM_ZONE_NOT_FOUND));
        // 다른 농장 소속 구역이면 존재 여부 자체를 노출하지 않기 위해 동일하게 FARM_ZONE_NOT_FOUND로 처리한다.
        if (!zone.getFarmId().equals(farmId)) {
            throw new BusinessException(ErrorCode.FARM_ZONE_NOT_FOUND);
        }
        return zone;
    }

    private FarmZoneEntity getZoneForUpdateOrThrow(Long farmId, Long zoneId) {
        FarmZoneEntity zone = farmZoneMapper.findByIdForUpdate(zoneId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARM_ZONE_NOT_FOUND));
        if (!zone.getFarmId().equals(farmId)) {
            throw new BusinessException(ErrorCode.FARM_ZONE_NOT_FOUND);
        }
        return zone;
    }

    private FarmResponse toFarmResponse(FarmSummaryRow row) {
        return new FarmResponse(row.getId(), row.getName(), row.getFarmCode(), row.getAddress(),
                row.getLifecycleStatus(), row.getStatus(), row.getRole());
    }

    private FarmZoneResponse toZoneResponse(FarmZoneEntity zone) {
        return new FarmZoneResponse(zone.getId(), zone.getName(), zone.getZoneType(), zone.getAreaValue(),
                zone.getAreaUnit(), zone.getDisplayOrder(), FarmZoneEntity.ACTIVE_YES.equals(zone.getActiveYn()));
    }

    private FarmZoneAssignmentResponse toAssignmentResponse(FarmZoneAssignmentEntity assignment) {
        return new FarmZoneAssignmentResponse(assignment.getId(), assignment.getZoneId(), assignment.getFarmId(),
                assignment.getEffectiveFrom(), assignment.getEffectiveTo(),
                FarmZoneAssignmentEntity.ACTIVE_YES.equals(assignment.getActiveYn()));
    }
}
