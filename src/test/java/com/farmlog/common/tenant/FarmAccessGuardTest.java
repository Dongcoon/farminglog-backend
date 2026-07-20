package com.farmlog.common.tenant;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * plan/02_Development_Guide.md 7.3 공통 검증 유틸({@code common/tenant})에 대한 단위 테스트.
 * 9장 Phase 2 DoD "농장 A 사용자가 농장 B 데이터를 조회할 수 없다"의 근거가 되는 핵심 로직을
 * FarmService와 무관하게(순수 Guard 단위로) 직접 검증한다.
 */
class FarmAccessGuardTest {

    private static final Long USER_ID = 10L;
    private static final Long FARM_A_ID = 1L;
    private static final Long FARM_B_ID = 2L;

    private FarmMembershipMapper farmMembershipMapper;
    private FarmAccessGuard farmAccessGuard;

    @BeforeEach
    void setUp() {
        farmMembershipMapper = mock(FarmMembershipMapper.class);
        farmAccessGuard = new FarmAccessGuard(farmMembershipMapper);
    }

    @Test
    void requireFarmMember_activeMember_returnsMembershipWithRole() {
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.of("FARM_OWNER"));

        FarmMembership membership = farmAccessGuard.requireFarmMember(USER_ID, FARM_A_ID);

        assertThat(membership.farmId()).isEqualTo(FARM_A_ID);
        assertThat(membership.userId()).isEqualTo(USER_ID);
        assertThat(membership.role()).isEqualTo("FARM_OWNER");
    }

    @Test
    void requireFarmMember_notAMemberAtAll_throwsForbidden() {
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> farmAccessGuard.requireFarmMember(USER_ID, FARM_A_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void requireFarmMember_memberOfOtherFarmOnly_throwsForbidden_crossFarmAccessRejected() {
        // 농장 A(1L)에는 ACTIVE로 소속되어 있지만 농장 B(2L)에는 소속이 없는 사용자가
        // 농장 B 데이터에 접근하려는 시나리오 — Phase 2 DoD 핵심 케이스.
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.of("FARM_OWNER"));
        when(farmMembershipMapper.findActiveMemberRole(FARM_B_ID, USER_ID)).thenReturn(Optional.empty());

        FarmMembership farmAMembership = farmAccessGuard.requireFarmMember(USER_ID, FARM_A_ID);
        assertThat(farmAMembership.role()).isEqualTo("FARM_OWNER");

        assertThatThrownBy(() -> farmAccessGuard.requireFarmMember(USER_ID, FARM_B_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void requireFarmMember_inactiveMembership_throwsForbidden() {
        // 탈퇴/비활성(status != ACTIVE) 멤버는 매퍼 쿼리 자체가 걸러내므로(WHERE status='ACTIVE'),
        // 매퍼가 empty를 반환하는 것으로 시뮬레이션한다.
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> farmAccessGuard.requireFarmMember(USER_ID, FARM_A_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void requireFarmRole_allowedRole_returnsMembership() {
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.of("FARM_MANAGER"));

        FarmMembership membership = farmAccessGuard.requireFarmRole(
                USER_ID, FARM_A_ID, "FARM_OWNER", "FARM_MANAGER");

        assertThat(membership.role()).isEqualTo("FARM_MANAGER");
    }

    @Test
    void requireFarmRole_memberWithoutAllowedRole_throwsForbidden() {
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID)).thenReturn(Optional.of("WORKER"));

        assertThatThrownBy(() -> farmAccessGuard.requireFarmRole(
                USER_ID, FARM_A_ID, "FARM_OWNER", "FARM_MANAGER"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void requireFarmRoleForUpdateRejectsMembershipRevokedWhileWaitingForParentLock() {
        when(farmMembershipMapper.findActiveMemberRole(FARM_A_ID, USER_ID))
                .thenReturn(Optional.of("FARM_OWNER"));
        when(farmMembershipMapper.findActiveMemberRoleForUpdate(FARM_A_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThat(farmAccessGuard.requireFarmRole(USER_ID, FARM_A_ID, "FARM_OWNER").role())
                .isEqualTo("FARM_OWNER");
        assertThatThrownBy(() -> farmAccessGuard.requireFarmRoleForUpdate(
                USER_ID, FARM_A_ID, "FARM_OWNER"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
