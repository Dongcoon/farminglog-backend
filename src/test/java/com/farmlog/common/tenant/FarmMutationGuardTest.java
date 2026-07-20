package com.farmlog.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FarmMutationGuardTest {

    @Test
    void locksAndReturnsOnlyActiveFarm() {
        FarmMapper mapper = mock(FarmMapper.class);
        FarmEntity farm = activeFarm();
        when(mapper.findByIdForUpdate(11L)).thenReturn(Optional.of(farm));

        assertThat(new FarmMutationGuard(mapper).lockActiveFarm(11L)).isSameAs(farm);
        verify(mapper).findByIdForUpdate(11L);
    }

    @Test
    void missingFarmIsNotFoundButInactiveFarmIsReadOnly() {
        FarmMapper mapper = mock(FarmMapper.class);
        FarmMutationGuard guard = new FarmMutationGuard(mapper);
        when(mapper.findByIdForUpdate(11L)).thenReturn(Optional.empty());
        when(mapper.findByIdForUpdate(12L)).thenReturn(Optional.of(
                FarmEntity.builder().id(12L).status("INACTIVE").lifecycleStatus("ACTIVE").build()));

        assertThatThrownBy(() -> guard.lockActiveFarm(11L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FARM_NOT_FOUND));
        assertThatThrownBy(() -> guard.lockActiveFarm(12L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FARM_READ_ONLY));
    }

    @Test
    void incrementsStructureVersionOnce() {
        FarmMapper mapper = mock(FarmMapper.class);
        when(mapper.incrementStructureVersion(any(), any(), any())).thenReturn(1);

        new FarmMutationGuard(mapper).incrementStructureVersion(11L, 7L);

        verify(mapper).incrementStructureVersion(any(), any(), any());
    }

    @Test
    void structureMutationLocksOrganizationBeforeFarm() {
        FarmMapper mapper = mock(FarmMapper.class);
        FarmEntity farm = activeFarm();
        when(mapper.findOrganizationId(11L)).thenReturn(Optional.of(3L));
        when(mapper.lockActiveOrganization(3L)).thenReturn(Optional.of(3L));
        when(mapper.findByIdForUpdate(11L)).thenReturn(Optional.of(farm));

        assertThat(new FarmMutationGuard(mapper).lockActiveStructureFarm(11L)).isSameAs(farm);

        var ordered = inOrder(mapper);
        ordered.verify(mapper).findOrganizationId(11L);
        ordered.verify(mapper).lockActiveOrganization(3L);
        ordered.verify(mapper).findByIdForUpdate(11L);
    }

    private FarmEntity activeFarm() {
        return FarmEntity.builder()
                .id(11L)
                .status(FarmEntity.STATUS_ACTIVE)
                .lifecycleStatus(FarmEntity.LIFECYCLE_ACTIVE)
                .structureVersion(0L)
                .build();
    }
}
