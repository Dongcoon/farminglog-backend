package com.farmlog.report.entity;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AggregateAverageRow {
  private Long farmId;
  private String unit;
  private BigDecimal soldQuantity, grossSalesAmount;
}
