package com.farmlog.organizationdashboard.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationHarvestRow {
  private Long farmId;
  private String unit;
  private BigDecimal currentQuantity;
  private BigDecimal previousQuantity;
  private long currentCount;
  private long previousCount;
}
