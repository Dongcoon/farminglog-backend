package com.farmlog.organizationdashboard.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationAverageRow {
  private Long farmId;
  private String unit;
  private BigDecimal soldQuantity;
  private BigDecimal grossSalesAmount;
}
