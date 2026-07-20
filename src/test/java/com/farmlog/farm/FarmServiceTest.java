package com.farmlog.farm;

import com.farmlog.auth.entity.OrganizationMemberEntity;
import com.farmlog.auth.mapper.AuthMapper;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMembershipMapper;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.farm.dto.FarmCreateRequest;
import com.farmlog.farm.dto.FarmResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FarmService 단위 테스트. Phase 1 AuthServiceTest와 동일하게 Mapper는 Mockito로 대체하고 실 DB는
 * 사용하지 않는다(plan/04_Open_Questions_Log.md Phase 1 항목 — MyBatis XML의 SQL 문법 자체는 이
 * 테스트로 검증되지 않음, 서비스 로직 검증에 집중).
 *
 * <p>단, {@link FarmAccessGuard}는 Mock이 아닌 실제 구현체를 사용하고 그 내부의
 * {@link FarmMembershipMapper}만 Mock으로 대체한다 — FarmService와 테넌트 검증 유틸이 실제로 연결되어
 * "농장 A 사용자가 농장 B 데이터를 조회할 수 없다"(9장 Phase 2 DoD)가 성립하는지까지 함께 검증하기
 * 위해서다.</p>
 */
class FarmServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long FARM_A_ID = 1L;
    private static final Long FARM_B_ID = 2L;

    private FarmMapper farmMapper;
    private FarmZoneMapper farmZoneMapper;
    private AuthMapper authMapper;
    private FarmMembershipMapper farmMembershipMapper;
    private FarmService farmService;

    @BeforeEach
    void setUp() {
        farmMapper = mock(FarmMapper.class);
        farmZoneMapper = mock(FarmZoneMapper.class);
        authMapper = mock(AuthMapper.class);
        farmMembershipMapper = mock(FarmMembershipMapper.class);
        FarmAccessGuard farmAccessGuard = new FarmAccessGuard(farmMembershipMapper);
        farmService = new FarmService(farmMapper, farmZoneMapper, authMapper, farmAccessGuard,
                new FarmMutationGuard(farmMapper));
        when(farmMapper.findOrganizationId(anyLong())).thenReturn(Optional.of(10L));
        when(farmMapper.lockActiveOrganization(anyLong()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        when(farmMembershipMapper.findActiveMemberRoleForUpdate(anyLong(), anyLong()))
                .thenReturn(Optional.of(FarmMemberEntity.ROLE_FARM_OWNER));
        when(farmZoneMapper.findCurrentAssignmentForUpdate(anyLong(), anyLong()))
                .thenReturn(Optional.of(1L));
        when(authMapper.findActiveOrganizationMembershipForUpdate(anyLong(), anyLong()))
                .thenAnswer(invocation -> Optional.of(OrganizationMemberEntity.builder()
                        .organizationId(invocation.getArgument(0)).userId(invocation.getArgument(1))
                        .role("ORG_ADMIN").status("ACTIVE").build()));

        AtomicLong idSeq = new AtomicLong(1);
        doAnswer(invocation -> {
            FarmEntity entity = invocation.getArgument(0);
            entity.setId(idSeq.getAndIncrement());
            return null;
        }).when(farmMapper).insert(any(FarmEntity.class));

        doAnswer(invocation -> {
            FarmMemberEntity entity = invocation.getArgument(0);
            entity.setId(idSeq.getAndIncrement());
            return null;
        }).when(farmMapper).insertMember(any(FarmMemberEntity.class));

        doAnswer(invocation -> {
            FarmZoneEntity entity = invocation.getArgument(0);
            entity.setId(idSeq.getAndIncrement());
            return null;
        }).when(farmZoneMapper).insert(any(FarmZoneEntity.class));

        doAnswer(invocation -> {
            FarmZoneAssignmentEntity entity = invocation.getArgument(0);
            entity.setId(idSeq.getAndIncrement());
            return null;
        }).when(farmZoneMapper).insertAssignment(any(FarmZoneAssignmentEntity.class));

        // 사용자는 농장 A에만 ACTIVE로 소속(이 테스트 클래스 전반의 기본 상태).
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.of("FARM_OWNER"));
        when(farmMembershipMapper.findActiveMemberRole(FARM_B_ID, USER_ID)).thenReturn(Optional.empty());
        when(farmMapper.findByIdForUpdate(FARM_A_ID)).thenReturn(Optional.of(activeFarm(FARM_A_ID)));
        when(farmMapper.incrementStructureVersion(eq(FARM_A_ID), eq(USER_ID), any())).thenReturn(1);
    }

    @Test
    void createFarm_success_createsFarmAndOwnerMemberFromOwnOrganization() {
        OrganizationMemberEntity membership = OrganizationMemberEntity.builder()
                .id(1L).organizationId(100L).userId(USER_ID).role("FARM_OWNER").status("ACTIVE").build();
        when(authMapper.findActiveOrganizationMembershipByUserId(USER_ID)).thenReturn(Optional.of(membership));

        FarmResponse response = farmService.createFarm(USER_ID, new FarmCreateRequest("딸기 농장", "전남 나주시"));

        assertThat(response.name()).isEqualTo("딸기 농장");
        assertThat(response.address()).isEqualTo("전남 나주시");
        assertThat(response.role()).isEqualTo("FARM_OWNER");
        assertThat(response.lifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(response.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<FarmEntity> farmCaptor = ArgumentCaptor.forClass(FarmEntity.class);
        verify(farmMapper, times(1)).insert(farmCaptor.capture());
        assertThat(farmCaptor.getValue().getOrganizationId()).isEqualTo(100L);
        assertThat(farmCaptor.getValue().getOwnerUserId()).isEqualTo(USER_ID);

        ArgumentCaptor<FarmMemberEntity> memberCaptor = ArgumentCaptor.forClass(FarmMemberEntity.class);
        verify(farmMapper, times(1)).insertMember(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo("FARM_OWNER");
        assertThat(memberCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void createFarm_noOrganizationMembership_throwsOrganizationNotFound() {
        when(authMapper.findActiveOrganizationMembershipByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> farmService.createFarm(USER_ID, new FarmCreateRequest("딸기 농장", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND));

        verify(farmMapper, never()).insert(any());
        verify(farmMapper, never()).insertMember(any());
    }

    @Test
    void listMyFarms_returnsFarmsWithCallerRole() {
        FarmSummaryRow row = FarmSummaryRow.builder()
                .id(FARM_A_ID).name("딸기 농장").farmCode(null).address("전남 나주시")
                .lifecycleStatus("ACTIVE").status("ACTIVE").role("FARM_OWNER").build();
        when(farmMapper.findActiveFarmSummariesForUser(USER_ID)).thenReturn(List.of(row));

        List<FarmResponse> result = farmService.listMyFarms(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(FARM_A_ID);
        assertThat(result.get(0).role()).isEqualTo("FARM_OWNER");
    }

    @Test
    void createZone_success_createsZoneAndAutoGeneratesAssignmentHistoryInSameTransaction() {
        FarmEntity farm = FarmEntity.builder().id(FARM_A_ID).organizationId(100L).name("딸기 농장")
                .lifecycleStatus("ACTIVE").status("ACTIVE").build();
        when(farmMapper.findByIdForUpdate(FARM_A_ID)).thenReturn(Optional.of(farm));

        FarmZoneResponse response = farmService.createZone(USER_ID, FARM_A_ID,
                new FarmZoneCreateRequest("1동", null, new BigDecimal("500.00"), "m2"));

        assertThat(response.name()).isEqualTo("1동");
        assertThat(response.zoneType()).isEqualTo("GREENHOUSE");
        assertThat(response.activeYn()).isTrue();

        verify(farmZoneMapper, times(1)).insert(any(FarmZoneEntity.class));

        // 9장 Phase 2 DoD: "구역 등록 시 farm_zone_assignment 이력 자동 생성 확인"
        ArgumentCaptor<FarmZoneAssignmentEntity> captor = ArgumentCaptor.forClass(FarmZoneAssignmentEntity.class);
        verify(farmZoneMapper, times(1)).insertAssignment(captor.capture());
        FarmZoneAssignmentEntity assignment = captor.getValue();
        assertThat(assignment.getFarmId()).isEqualTo(FARM_A_ID);
        assertThat(assignment.getOrganizationId()).isEqualTo(100L);
        assertThat(assignment.getEffectiveFrom()).isEqualTo(LocalDate.now());
        assertThat(assignment.getEffectiveTo()).isNull();
        assertThat(assignment.getChangeEventId()).isNull();
        assertThat(assignment.getActiveYn()).isEqualTo("Y");
    }

    @Test
    void createZone_explicitZoneType_isPreservedInsteadOfDefault() {
        FarmEntity farm = activeFarm(FARM_A_ID);
        when(farmMapper.findByIdForUpdate(FARM_A_ID)).thenReturn(Optional.of(farm));

        FarmZoneResponse response = farmService.createZone(USER_ID, FARM_A_ID,
                new FarmZoneCreateRequest("노지 A", "FIELD", null, null));

        assertThat(response.zoneType()).isEqualTo("FIELD");
    }

    @Test
    void createZone_farmNotFound_throwsFarmNotFound() {
        when(farmMapper.findByIdForUpdate(FARM_A_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> farmService.createZone(USER_ID, FARM_A_ID,
                new FarmZoneCreateRequest("1동", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FARM_NOT_FOUND));
    }

    @Test
    void listZones_userNotMemberOfFarm_throwsForbidden_crossFarmAccessRejected() {
        // 농장 A 사용자가 농장 B(FARM_B_ID)의 구역 목록을 조회하려는 시도 — Phase 2 DoD 핵심 케이스
        // ("농장 A 사용자가 농장 B 데이터를 API로 조회 불가").
        assertThatThrownBy(() -> farmService.listZones(USER_ID, FARM_B_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(farmZoneMapper, never()).findByFarmId(any(), anyBoolean());
    }

    @Test
    void createZone_userNotMemberOfFarm_throwsForbidden_beforeTouchingData() {
        assertThatThrownBy(() -> farmService.createZone(USER_ID, FARM_B_ID,
                new FarmZoneCreateRequest("1동", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(farmMapper, never()).findById(any());
        verify(farmZoneMapper, never()).insert(any());
        verify(farmZoneMapper, never()).insertAssignment(any());
    }

    @Test
    void createZone_workerRole_throwsForbidden_beforeTouchingData() {
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.of("WORKER"));

        assertThatThrownBy(() -> farmService.createZone(USER_ID, FARM_A_ID,
                new FarmZoneCreateRequest("1동", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(farmMapper, never()).findById(any());
        verify(farmZoneMapper, never()).insert(any());
        verify(farmZoneMapper, never()).insertAssignment(any());
    }

    @Test
    void createZone_farmManagerRole_isAllowed() {
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.of("FARM_MANAGER"));
        FarmEntity farm = activeFarm(FARM_A_ID);
        when(farmMapper.findByIdForUpdate(FARM_A_ID)).thenReturn(Optional.of(farm));

        FarmZoneResponse response = farmService.createZone(USER_ID, FARM_A_ID,
                new FarmZoneCreateRequest("2동", null, null, null));

        assertThat(response.name()).isEqualTo("2동");
        verify(farmZoneMapper).insert(any(FarmZoneEntity.class));
        verify(farmZoneMapper).insertAssignment(any(FarmZoneAssignmentEntity.class));
    }

    @Test
    void updateZone_zoneBelongsToDifferentFarm_throwsFarmZoneNotFound() {
        FarmZoneEntity zoneOfFarmB = FarmZoneEntity.builder().id(50L).farmId(FARM_B_ID).name("다른 농장 구역").build();
        when(farmZoneMapper.findByIdForUpdate(50L)).thenReturn(Optional.of(zoneOfFarmB));

        FarmZoneUpdateRequest request = new FarmZoneUpdateRequest();
        request.setName("새 이름");

        assertThatThrownBy(() -> farmService.updateZone(USER_ID, FARM_A_ID, 50L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FARM_ZONE_NOT_FOUND));

        verify(farmZoneMapper, never()).update(any());
    }

    @Test
    void updateZone_workerRole_throwsForbidden_beforeReadingZone() {
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.of("WORKER"));
        FarmZoneUpdateRequest request = new FarmZoneUpdateRequest();
        request.setName("새 이름");

        assertThatThrownBy(() -> farmService.updateZone(USER_ID, FARM_A_ID, 50L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(farmZoneMapper, never()).findById(any());
        verify(farmZoneMapper, never()).update(any());
    }

    @Test
    void updateZone_partialUpdate_onlyChangesProvidedFields() {
        FarmZoneEntity zone = FarmZoneEntity.builder().id(50L).farmId(FARM_A_ID).name("1동")
                .zoneType("GREENHOUSE").areaValue(new BigDecimal("300.00")).areaUnit("m2")
                .displayOrder(1).activeYn("Y").build();
        when(farmZoneMapper.findByIdForUpdate(50L)).thenReturn(Optional.of(zone));

        FarmZoneUpdateRequest request = new FarmZoneUpdateRequest();
        request.setDisplayOrder(5);

        FarmZoneResponse response = farmService.updateZone(USER_ID, FARM_A_ID, 50L, request);

        assertThat(response.name()).isEqualTo("1동");
        assertThat(response.displayOrder()).isEqualTo(5);
        verify(farmZoneMapper, times(1)).update(eq(zone));
    }

    @Test
    void updateZone_changesZoneType_andClearsExplicitlyNullAreaFields() {
        FarmZoneEntity zone = FarmZoneEntity.builder().id(50L).farmId(FARM_A_ID).name("1동")
                .zoneType("GREENHOUSE").areaValue(new BigDecimal("300.00")).areaUnit("m2")
                .displayOrder(1).activeYn("Y").build();
        when(farmZoneMapper.findByIdForUpdate(50L)).thenReturn(Optional.of(zone));

        FarmZoneUpdateRequest request = new FarmZoneUpdateRequest();
        request.setZoneType("FIELD");
        request.setAreaValue(null);
        request.setAreaUnit(null);

        FarmZoneResponse response = farmService.updateZone(USER_ID, FARM_A_ID, 50L, request);

        assertThat(response.zoneType()).isEqualTo("FIELD");
        assertThat(response.areaValue()).isNull();
        assertThat(response.areaUnit()).isNull();
        verify(farmZoneMapper).update(eq(zone));
    }

    @Test
    void deactivateZone_setsActiveNo_andDoesNotTouchAssignmentHistory() {
        FarmZoneEntity zone = FarmZoneEntity.builder().id(60L).farmId(FARM_A_ID).name("1동").activeYn("Y").build();
        when(farmZoneMapper.findByIdForUpdate(60L)).thenReturn(Optional.of(zone));

        FarmZoneResponse response = farmService.deactivateZone(USER_ID, FARM_A_ID, 60L);

        assertThat(response.activeYn()).isFalse();
        verify(farmZoneMapper, times(1)).deactivate(eq(FARM_A_ID), eq(60L), eq(USER_ID), any(LocalDateTime.class));
        verify(farmZoneMapper, never()).insertAssignment(any());
        verify(farmZoneMapper, never()).update(any());
    }

    @Test
    void listZoneAssignments_userNotMemberOfFarm_throwsForbidden() {
        assertThatThrownBy(() -> farmService.listZoneAssignments(USER_ID, FARM_B_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(farmZoneMapper, never()).findAssignmentsByFarmId(any());
    }

    private FarmEntity activeFarm(Long id) {
        return FarmEntity.builder()
                .id(id)
                .organizationId(100L)
                .status(FarmEntity.STATUS_ACTIVE)
                .lifecycleStatus(FarmEntity.LIFECYCLE_ACTIVE)
                .structureVersion(0L)
                .build();
    }
}
