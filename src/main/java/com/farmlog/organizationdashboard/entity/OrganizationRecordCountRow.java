package com.farmlog.organizationdashboard.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationRecordCountRow {
  private Long farmId;
  private String recordType;
  private long recordCount;
  private long managerInputCount;
}
