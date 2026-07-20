package com.farmlog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.user.mapper.UserAccessMapper;
import org.junit.jupiter.api.Test;

class AdminAccessGuardTest {
  @Test
  void checksActiveSystemAdminCapabilityFromDatabase() {
    UserAccessMapper mapper = mock(UserAccessMapper.class);
    AdminAccessGuard guard = new AdminAccessGuard(mapper);
    when(mapper.existsActiveSystemAdmin(7L)).thenReturn(true);

    guard.requireSystemAdmin(7L);

    when(mapper.existsActiveSystemAdmin(8L)).thenReturn(false);
    assertThatThrownBy(() -> guard.requireSystemAdmin(8L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
  }
}
