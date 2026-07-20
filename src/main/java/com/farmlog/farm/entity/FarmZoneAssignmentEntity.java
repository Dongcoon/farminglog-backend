package com.farmlog.farm.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code farm_zone_assignment} 테이블 매핑 엔티티 — 구역이 어느 기간에 어느 농장에 속했는지 관리하는
 * 이력 테이블(docs/Farmlog_ERP_Developer_Design.md 5.2). 구역 등록({@code POST /farms/{farmId}/zones})
 * 시 같은 트랜잭션에서 자동으로 1건 생성된다({@code change_event_id=null}, {@code effective_from}=등록일,
 * {@code effective_to}=null). 병합/분리(Phase 8)에서 {@code change_event_id}와 {@code effective_to}가
 * 채워지는 것을 전제로 스키마가 설계되어 있다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmZoneAssignmentEntity {

    public static final String ACTIVE_YES = "Y";

    private Long id;
    private Long organizationId;
    private Long zoneId;
    private Long farmId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Long changeEventId;
    private String activeYn;
    private Long version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
