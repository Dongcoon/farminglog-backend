package com.farmlog.masterdata.entity;

import lombok.Getter;
import lombok.Setter;

/** work_type/customer/material의 공통 농장 로컬 조회 모델. */
@Getter
@Setter
public class LocalMasterDataRow {
    private Long id;
    private Long organizationId;
    private Long farmId;
    private String name;
    private String type;
    private String phone;
    private String unit;
    private String memo;
    private Integer displayOrder;
    private String activeYn;
}
