package com.farmlog.admin.mapper;

import com.farmlog.admin.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMapper {
  List<AdminUserRow> findUsers(
      @Param("q") String q,
      @Param("status") String status,
      @Param("organizationId") Long organizationId,
      @Param("orderBy") String orderBy,
      @Param("direction") String direction,
      @Param("offset") int offset,
      @Param("size") int size);

  long countUsers(
      @Param("q") String q,
      @Param("status") String status,
      @Param("organizationId") Long organizationId);

  List<AdminFarmRow> findFarms(
      @Param("q") String q,
      @Param("organizationId") Long organizationId,
      @Param("ownerUserId") Long ownerUserId,
      @Param("lifecycleStatus") String lifecycleStatus,
      @Param("status") String status,
      @Param("orderBy") String orderBy,
      @Param("direction") String direction,
      @Param("offset") int offset,
      @Param("size") int size);

  long countFarms(
      @Param("q") String q,
      @Param("organizationId") Long organizationId,
      @Param("ownerUserId") Long ownerUserId,
      @Param("lifecycleStatus") String lifecycleStatus,
      @Param("status") String status);

  List<AdminAuditRow> findAuditLogs(
      @Param("organizationId") Long organizationId,
      @Param("farmId") Long farmId,
      @Param("actorUserId") Long actorUserId,
      @Param("action") String action,
      @Param("targetType") String targetType,
      @Param("targetId") Long targetId,
      @Param("createdFrom") LocalDateTime createdFrom,
      @Param("createdToExclusive") LocalDateTime createdToExclusive,
      @Param("orderBy") String orderBy,
      @Param("direction") String direction,
      @Param("offset") int offset,
      @Param("size") int size);

  long countAuditLogs(
      @Param("organizationId") Long organizationId,
      @Param("farmId") Long farmId,
      @Param("actorUserId") Long actorUserId,
      @Param("action") String action,
      @Param("targetType") String targetType,
      @Param("targetId") Long targetId,
      @Param("createdFrom") LocalDateTime createdFrom,
      @Param("createdToExclusive") LocalDateTime createdToExclusive);

  void insertViewAudit(
      @Param("actorUserId") Long actorUserId,
      @Param("action") String action,
      @Param("ipAddress") String ipAddress,
      @Param("userAgent") String userAgent,
      @Param("detailJson") String detailJson,
      @Param("createdAt") LocalDateTime createdAt);
}
