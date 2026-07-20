package com.farmlog.farmstructure.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class StructureFarmRow {
  private Long id; private Long organizationId; private String farmCode; private String name; private Long ownerUserId;
  private String lifecycleStatus; private String status; private Long structureVersion; private LocalDateTime createdAt;
  private Long currentZoneCount;
}
