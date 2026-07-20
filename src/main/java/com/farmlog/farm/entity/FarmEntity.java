package com.farmlog.farm.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code farm} 테이블 매핑 엔티티. 관리/정산/권한의 단위(docs/Farmlog_ERP_Developer_Design.md 5.2 "농장
 * 병합/분리 모델" — {@code farm}은 실제 하우스/필지({@code farm_zone})와 분리된 상위 단위임에 유의).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmEntity {

    public static final String LIFECYCLE_ACTIVE = "ACTIVE";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long id;
    private Long organizationId;
    private String farmCode;
    private String name;
    private Long ownerUserId;
    private Long mainCropId;
    private String address;
    private String memo;
    private String lifecycleStatus;
    private String status;
    private Long structureVersion;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
