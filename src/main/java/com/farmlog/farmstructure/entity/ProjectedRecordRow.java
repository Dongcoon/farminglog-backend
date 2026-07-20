package com.farmlog.farmstructure.entity;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 구조 reducer가 immutable recorded FK를 유지한 채 farm 차원만 투영하는 원시 행. */
@Getter @Setter
public class ProjectedRecordRow {
  private String recordType; private Long recordId; private Long farmId; private Long zoneId; private LocalDate recordDate;
  private String createdRole; private String unit; private BigDecimal quantity; private BigDecimal grossAmount;
  private BigDecimal feeAmount; private BigDecimal netAmount; private Long varietyId; private String varietyName;
  private Long zoneBreakdownId; private String zoneName; private Long customerId; private String customerName;
}
