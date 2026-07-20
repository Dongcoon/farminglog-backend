package com.farmlog.report.entity;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RecordCountRow {
    private String recordType;
    private long recordCount;
    private long managerInputCount;
}
