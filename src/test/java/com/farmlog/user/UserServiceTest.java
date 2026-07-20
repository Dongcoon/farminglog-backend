package com.farmlog.user;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.user.dto.UserPreferenceRequest;
import com.farmlog.user.dto.UserPreferenceResponse;
import com.farmlog.user.entity.OrganizationAccessRow;
import com.farmlog.user.entity.UserEntity;
import com.farmlog.user.entity.UserPreferenceEntity;
import com.farmlog.user.mapper.UserAccessMapper;
import com.farmlog.user.mapper.UserMapper;
import com.farmlog.user.mapper.UserPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserMapper userMapper;
    private UserPreferenceMapper userPreferenceMapper;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userPreferenceMapper = mock(UserPreferenceMapper.class);
        userService = new UserService(userMapper, userPreferenceMapper);

        when(userMapper.findById(1L)).thenReturn(Optional.of(
                UserEntity.builder().id(1L).email("farmer@example.com").displayName("홍길동").status("ACTIVE").build()));
    }

    @Test
    void getMyPreferences_noRowYet_returnsDefaultStandardScale() {
        when(userPreferenceMapper.findByUserId(1L)).thenReturn(Optional.empty());

        UserPreferenceResponse response = userService.getMyPreferences(1L);

        assertThat(response.viewScale()).isEqualTo("standard");
        assertThat(response.outdoorMode()).isFalse();
        assertThat(response.reduceMotion()).isFalse();
    }

    @Test
    void updateMyPreferences_noRowYet_insertsNewRow() {
        when(userPreferenceMapper.findByUserId(1L)).thenReturn(Optional.empty());

        UserPreferenceResponse response = userService.updateMyPreferences(1L,
                new UserPreferenceRequest("large", true, false));

        assertThat(response.viewScale()).isEqualTo("large");
        assertThat(response.outdoorMode()).isTrue();
        verify(userPreferenceMapper, times(1)).insert(any(UserPreferenceEntity.class));
        verify(userPreferenceMapper, never()).update(any());
    }

    @Test
    void updateMyPreferences_existingRow_updatesInPlace() {
        UserPreferenceEntity existing = UserPreferenceEntity.builder()
                .id(5L).userId(1L).viewScale("standard").outdoorModeYn("N").reduceMotionYn("N").build();
        when(userPreferenceMapper.findByUserId(1L)).thenReturn(Optional.of(existing));

        UserPreferenceResponse response = userService.updateMyPreferences(1L,
                new UserPreferenceRequest("max", false, true));

        assertThat(response.viewScale()).isEqualTo("max");
        assertThat(response.reduceMotion()).isTrue();
        verify(userPreferenceMapper, times(1)).update(any(UserPreferenceEntity.class));
        verify(userPreferenceMapper, never()).insert(any());
    }

    @Test
    void getMyProfile_unknownUser_throwsUserNotFound() {
        when(userMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void accessContextSeparatesSystemRolesFromOrganizations() {
      UserAccessMapper accessMapper = mock(UserAccessMapper.class);
      userService = new UserService(userMapper, userPreferenceMapper, accessMapper);
      OrganizationAccessRow system = organization(100L, "시스템", "SYSTEM", "SYSTEM_ADMIN");
      OrganizationAccessRow coop = organization(200L, "행복 작목반", "COOP", "ORG_ADMIN");
      when(accessMapper.findActiveOrganizations(1L)).thenReturn(List.of(system, coop));

      var response = userService.getAccessContext(1L);

      assertThat(response.userId()).isEqualTo(1L);
      assertThat(response.systemRoles()).containsExactly("SYSTEM_ADMIN");
      assertThat(response.organizations()).extracting(item -> item.id()).containsExactly(100L, 200L);
    }

    @Test
    void preferenceValidationAlsoAppliesWhenServiceIsCalledDirectly() {
      assertThatThrownBy(
              () ->
                  userService.updateMyPreferences(1L, new UserPreferenceRequest("huge", true, false)))
          .isInstanceOfSatisfying(
              BusinessException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
      verify(userPreferenceMapper, never()).insert(any());
    }

    @Test
    void concurrentFirstInsertRetriesAsUpdateWithoutLosingLastFarm() {
      UserPreferenceEntity concurrent =
          UserPreferenceEntity.builder()
              .id(8L)
              .userId(1L)
              .viewScale("standard")
              .outdoorModeYn("N")
              .reduceMotionYn("N")
              .lastSelectedFarmId(77L)
              .build();
      when(userPreferenceMapper.findByUserId(1L))
          .thenReturn(Optional.empty(), Optional.of(concurrent));
      org.mockito.Mockito.doThrow(new DuplicateKeyException("동시 생성"))
          .when(userPreferenceMapper)
          .insert(any(UserPreferenceEntity.class));

      var response =
          userService.updateMyPreferences(1L, new UserPreferenceRequest("xlarge", true, true));

      assertThat(response.lastSelectedFarmId()).isEqualTo(77L);
      assertThat(response.viewScale()).isEqualTo("xlarge");
      verify(userPreferenceMapper).update(concurrent);
    }

    private OrganizationAccessRow organization(long id, String name, String type, String role) {
      OrganizationAccessRow row = new OrganizationAccessRow();
      row.setId(id);
      row.setName(name);
      row.setOrgType(type);
      row.setRole(role);
      row.setStatus("ACTIVE");
      return row;
    }
}
