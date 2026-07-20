package com.farmlog.admin;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.user.mapper.UserAccessMapper;
import org.springframework.stereotype.Service;

/** JWT나 현재 농장 역할을 신뢰하지 않고 매 요청마다 DB의 SYSTEM capability를 확인한다. */
@Service
public class AdminAccessGuard {
  private final UserAccessMapper mapper;

  public AdminAccessGuard(UserAccessMapper mapper) {
    this.mapper = mapper;
  }

  public void requireSystemAdmin(Long userId) {
    if (!mapper.existsActiveSystemAdmin(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
  }
}
