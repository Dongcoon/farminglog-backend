package com.farmlog.farmaccess;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.farmaccess.entity.CareAssignmentRow;
import com.farmlog.farmaccess.mapper.FarmAccessMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** care manager 접근은 farm_member 역할이 아니라 현재 유효한 배정 기간으로만 판정한다. */
@Service
public class CareAccessGuard {
  private final FarmAccessMapper mapper;

  public CareAccessGuard(FarmAccessMapper mapper) {
    this.mapper = mapper;
  }

  public Optional<CareAssignmentRow> findActive(Long userId, Long farmId) {
    return mapper.findActiveAssignment(farmId, userId, LocalDateTime.now());
  }

  public CareAssignmentRow requireActive(Long userId, Long farmId) {
    return findActive(userId, farmId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "현재 유효한 매니저 배정이 없습니다."));
  }

  /** 쓰기 트랜잭션은 배정 행을 잠가 회수와 직렬화한다. */
  public CareAssignmentRow requireActiveForUpdate(Long userId, Long farmId) {
    return mapper
        .findActiveAssignmentForUpdate(farmId, userId, LocalDateTime.now())
        .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "현재 유효한 매니저 배정이 없습니다."));
  }
}
