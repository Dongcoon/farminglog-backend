package com.farmlog.farmaccess.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberRow {
  private Long id, farmId, userId, version;
  private String email, displayName, role, status;
  private LocalDateTime createdAt, updatedAt;
}
