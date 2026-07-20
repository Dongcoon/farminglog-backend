package com.farmlog.organizationdashboard.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationSalesRow {
  private Long farmId;
  private BigDecimal currentGross;
  private BigDecimal currentFee;
  private BigDecimal currentNet;
  private BigDecimal previousGross;
  private BigDecimal previousFee;
  private BigDecimal previousNet;
  private long currentCount;
  private long previousCount;
}
