package com.farmlog.organizationdashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.organizationdashboard.entity.OrganizationAccessRow;
import com.farmlog.organizationdashboard.mapper.OrganizationAccessMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrganizationAccessGuardTest {
  @Test
  void unauthorizedOrganizationIsHiddenAsNotFound() {
    OrganizationAccessMapper mapper=mock(OrganizationAccessMapper.class);
    when(mapper.findActiveOrgAdmin(7L,99L)).thenReturn(Optional.empty());
    OrganizationAccessGuard guard=new OrganizationAccessGuard(mapper);

    assertThatThrownBy(()->guard.requireActiveOrgAdmin(7L,99L))
        .isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND));
  }

  @Test
  void exactActiveOrgAdminIsReturned() {
    OrganizationAccessMapper mapper=mock(OrganizationAccessMapper.class);
    OrganizationAccessRow row=new OrganizationAccessRow();row.setId(99L);row.setStatus("ACTIVE");
    when(mapper.findActiveOrgAdmin(7L,99L)).thenReturn(Optional.of(row));
    assertThat(new OrganizationAccessGuard(mapper).requireActiveOrgAdmin(7L,99L)).isSameAs(row);
  }
}
