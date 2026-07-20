package com.farmlog.organizationdashboard.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 목록/상세에서 허용된 최소 identity만 담고 원문 메모·연락처는 매핑하지 않는다. */
@Getter
@Setter
public class OrganizationFarmRow {
  private Long id;
  private String farmCode;
  private String name;
  private String address;
  private String lifecycleStatus;
  private String status;
  private Long ownerUserId;
  private String ownerDisplayName;
  private String ownerEmail;
  private Long mainCropId;
  private String mainCropName;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private long activeMemberCount;
  private long activeZoneCount;
}
