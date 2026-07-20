package com.farmlog.farmstructure;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.farmstructure.mapper.FarmStructureMapper;
import org.springframework.stereotype.Service;
import java.util.List;

/** JWT/currentFarmRole이 아니라 매 요청 DB 상태로 구조 변경 capability를 확인한다. */
@Service
public class StructureAccessGuard {
  private final FarmStructureMapper mapper;
  public StructureAccessGuard(FarmStructureMapper mapper) { this.mapper = mapper; }
  public boolean system(Long userId) { return mapper.isSystemAdmin(userId); }
  public boolean organizationAdmin(Long userId, Long organizationId) {
    return system(userId) || mapper.isOrgAdmin(userId, organizationId);
  }
  public void requireAll(Long userId, Long organizationId, List<Long> farmIds) {
    if (organizationAdmin(userId, organizationId)) return;
    if (farmIds.isEmpty() || mapper.countOwnedRelated(userId, farmIds) != farmIds.size()) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }
}
