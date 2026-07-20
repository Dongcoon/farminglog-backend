package com.farmlog.report.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AggregateHarvestRow {
  private Long farmId;
  private String unit;
  private BigDecimal currentQuantity, previousQuantity;
  private long currentCount, previousCount;
}
