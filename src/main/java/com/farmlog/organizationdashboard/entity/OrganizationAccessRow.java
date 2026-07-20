package com.farmlog.organizationdashboard.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationAccessRow {
  private Long id;
  private String name;
  private String orgType;
  private String status;
}
