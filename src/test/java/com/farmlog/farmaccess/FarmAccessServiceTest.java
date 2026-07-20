package com.farmlog.farmaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmaccess.dto.CareAssignmentRequests;
import com.farmlog.farmaccess.dto.MemberUpdateRequest;
import com.farmlog.farmaccess.entity.InvitationRow;
import com.farmlog.farmaccess.entity.MemberRow;
import com.farmlog.farmaccess.mapper.FarmAccessMapper;
import com.farmlog.user.entity.UserEntity;
import com.farmlog.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FarmAccessServiceTest {
  @Test
  void acceptNeverOverwritesMembershipCreatedAfterInvitation() {
    FarmAccessMapper mapper = mock(FarmAccessMapper.class);
    FarmAccessService service =
        new FarmAccessService(
            mapper,
            mock(FarmAccessGuard.class),
            mock(FarmMapper.class),
            mock(UserMapper.class),
            new ObjectMapper(),
            mock(InvitationDelivery.class),
            mock(FarmMutationGuard.class));
    InvitationRow invitation = new InvitationRow();
    invitation.setId(3L);
    invitation.setFarmId(11L);
    invitation.setEmail("user@example.com");
    invitation.setRole("WORKER");
    invitation.setStatus("PENDING");
    invitation.setVersion(0L);
    invitation.setExpiresAt(LocalDateTime.now().plusDays(1));
    MemberRow owner = new MemberRow();
    owner.setFarmId(11L);
    owner.setUserId(7L);
    owner.setRole("FARM_OWNER");
    owner.setStatus("ACTIVE");
    when(mapper.findInvitationByTokenForUpdate(anyString(), any()))
        .thenReturn(Optional.of(invitation));
    when(mapper.findInvitationByToken(anyString(), any())).thenReturn(Optional.of(invitation));
    when(mapper.findMemberByUserForUpdate(11L, 7L)).thenReturn(Optional.of(owner));

    assertThatThrownBy(() -> service.acceptInvitation(7L, "USER@example.com", "raw-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    verify(mapper, never()).upsertAcceptedMember(anyLong(), anyLong(), anyString(), any());
    verify(mapper, never()).acceptInvitation(anyLong(), anyLong(), anyLong(), any());
  }

  @Test
  void overlappingAssignmentIsRejectedAfterScopeLock() {
    FarmAccessMapper mapper = mock(FarmAccessMapper.class);
    FarmAccessGuard guard = mock(FarmAccessGuard.class);
    UserMapper users = mock(UserMapper.class);
    FarmAccessService service =
        new FarmAccessService(
            mapper,
            guard,
            mock(FarmMapper.class),
            users,
            new ObjectMapper(),
            mock(InvitationDelivery.class),
            mock(FarmMutationGuard.class));
    when(guard.requireFarmRole(7L, 11L, "FARM_OWNER"))
        .thenReturn(new com.farmlog.common.tenant.FarmMembership(11L, 7L, "FARM_OWNER"));
    UserEntity manager =
        UserEntity.builder().id(9L).email("care@example.com").status("ACTIVE").build();
    when(users.findByEmail("care@example.com")).thenReturn(Optional.of(manager));
    when(mapper.lockAssignmentScope(11L, 9L)).thenReturn(Optional.of(11L));
    when(mapper.countOverlappingAssignments(eq(11L), eq(9L), any(), any())).thenReturn(1L);
    var request =
        new CareAssignmentRequests.Create(
            "123e4567-e89b-12d3-a456-426614174030",
            "care@example.com",
            null,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(1));

    assertThatThrownBy(() -> service.createAssignment(7L, 11L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    verify(mapper).lockAssignmentScope(11L, 9L);
    verify(mapper, never()).insertAssignment(any());
  }

  @Test
  void lastActiveOwnerCannotBeDemotedAfterOwnerRowsAreLocked() {
    FarmAccessMapper mapper = mock(FarmAccessMapper.class);
    FarmAccessGuard guard = mock(FarmAccessGuard.class);
    FarmAccessService service =
        new FarmAccessService(
            mapper,
            guard,
            mock(FarmMapper.class),
            mock(UserMapper.class),
            new ObjectMapper(),
            mock(InvitationDelivery.class),
            mock(FarmMutationGuard.class));
    when(guard.requireFarmRole(7L, 11L, "FARM_OWNER"))
        .thenReturn(new com.farmlog.common.tenant.FarmMembership(11L, 7L, "FARM_OWNER"));
    MemberRow owner = new MemberRow();
    owner.setId(4L);
    owner.setFarmId(11L);
    owner.setUserId(7L);
    owner.setRole("FARM_OWNER");
    owner.setStatus("ACTIVE");
    owner.setVersion(0L);
    when(mapper.lockActiveOwners(11L)).thenReturn(List.of(4L));
    when(mapper.findMember(11L, 4L)).thenReturn(Optional.of(owner));

    assertThatThrownBy(
            () ->
                service.updateMember(
                    7L, 11L, 4L, new MemberUpdateRequest("FARM_MANAGER", "ACTIVE", 0L)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    verify(mapper).lockActiveOwners(11L);
    verify(mapper, never())
        .updateMember(anyLong(), anyLong(), anyString(), anyString(), anyLong(), any());
  }
}
