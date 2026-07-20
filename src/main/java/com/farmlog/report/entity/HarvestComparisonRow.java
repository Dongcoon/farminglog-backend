package com.farmlog.report.entity;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class HarvestComparisonRow {
    private String unit;
    private BigDecimal currentQuantity, previousQuantity;
    private long currentCount, previousCount;
}
