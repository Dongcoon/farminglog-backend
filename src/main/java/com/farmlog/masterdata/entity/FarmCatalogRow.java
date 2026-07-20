package com.farmlog.masterdata.entity;

import lombok.Getter;
import lombok.Setter;

/** 농장 연결과 불변 전역 카탈로그를 조인한 조회 행. */
@Getter
@Setter
public class FarmCatalogRow {
    private Long linkId;
    private Long farmId;
    private Long cropId;
    private Long varietyId;
    private String name;
    private Integer displayOrder;
    private String activeYn;
}
