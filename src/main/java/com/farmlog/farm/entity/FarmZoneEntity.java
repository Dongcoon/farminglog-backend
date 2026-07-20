package com.farmlog.farm.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code farm_zone} 테이블 매핑 엔티티 — 실제 하우스/필지/구역(docs/Farmlog_ERP_Developer_Design.md 5.2).
 * 비활성화는 소프트 삭제({@code active_yn='N'})로만 처리하며, 하드 삭제는 하지 않는다(9장 Phase 3
 * DoD "비활성화된 항목이 과거 기록엔 유지"와 동일한 원칙을 Phase 2부터 적용).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmZoneEntity {

    public static final String DEFAULT_ZONE_TYPE = "GREENHOUSE";
    public static final String ACTIVE_YES = "Y";
    public static final String ACTIVE_NO = "N";

    private Long id;
    private Long organizationId;
    private Long farmId;
    private String name;
    private String zoneType;
    private BigDecimal areaValue;
    private String areaUnit;
    private Integer displayOrder;
    private String activeYn;
    private Long version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
