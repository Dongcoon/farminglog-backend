package com.farmlog.report.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AggregateRecordCountRow {
  private Long farmId;
  private String recordType;
  private long recordCount, managerInputCount;
}
