package com.farmlog.organizationdashboard;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.organizationdashboard.entity.OrganizationAccessRow;
import com.farmlog.organizationdashboard.mapper.OrganizationAccessMapper;
import org.springframework.stereotype.Service;

/**
 * 조직 대시보드는 farm 역할이나 care 권한을 승계하지 않고 현재 DB의 exact ORG_ADMIN만 허용한다.
 * 권한이 없는 ID도 존재 여부를 숨기기 위해 ORGANIZATION_NOT_FOUND로 통일한다.
 */
@Service
public class OrganizationAccessGuard {
  private final OrganizationAccessMapper mapper;

  public OrganizationAccessGuard(OrganizationAccessMapper mapper) {
    this.mapper = mapper;
  }

  public OrganizationAccessRow requireActiveOrgAdmin(Long userId, Long organizationId) {
    if (userId == null || organizationId == null || organizationId < 1) {
      throw new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND);
    }
    return mapper.findActiveOrgAdmin(userId, organizationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
  }
}
