package com.farmlog.report.entity;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class SalesComparisonRow {
    private BigDecimal currentGross, currentFee, currentNet, previousGross, previousFee, previousNet;
    private long currentCount, previousCount;
}
