package com.farmlog.report.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AggregateSalesRow {
  private Long farmId;
  private BigDecimal currentGross, currentFee, currentNet, previousGross, previousFee, previousNet;
  private long currentCount, previousCount;
}
