package com.farmlog.user.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationAccessRow {
  private Long id;
  private String name;
  private String orgType;
  private String role;
  private String status;
}
