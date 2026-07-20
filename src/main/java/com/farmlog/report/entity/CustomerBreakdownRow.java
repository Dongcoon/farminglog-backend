package com.farmlog.report.entity;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class CustomerBreakdownRow {
    private Long id;
    private String name;
    private BigDecimal grossSalesAmount, feeAmount, netSalesAmount;
    private long recordCount, managerInputCount;
}
