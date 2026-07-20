package com.farmlog.user.dto;

import java.util.List;

/** 농장 역할과 분리된 전역·조직 capability 응답이다. */
public record AccessContextResponse(
    Long userId, List<String> systemRoles, List<OrganizationAccess> organizations) {
  public record OrganizationAccess(
      Long id, String name, String orgType, String role, String status) {}
}
