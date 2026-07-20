package com.farmlog.report.entity;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class AveragePriceRow {
    private String unit;
    private BigDecimal soldQuantity, grossSalesAmount;
}
