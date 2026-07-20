package com.farmlog.dataquality.mapper;

import com.farmlog.dataquality.entity.*;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DataQualityMapper {
  List<IssueRow> findPage(
      @Param("farmId") Long farmId,
      @Param("issueType") String issueType,
      @Param("issueStatus") String issueStatus,
      @Param("severity") String severity,
      @Param("farmerConfirmStatus") String confirm,
      @Param("orderBy") String orderBy,
      @Param("direction") String direction,
      @Param("offset") int offset,
      @Param("size") int size);

  long countPage(
      @Param("farmId") Long farmId,
      @Param("issueType") String issueType,
      @Param("issueStatus") String issueStatus,
      @Param("severity") String severity,
      @Param("farmerConfirmStatus") String confirm);

  Optional<IssueRow> findById(@Param("farmId") Long farmId, @Param("id") Long id);

  List<AttachmentSummaryRow> findIssueAttachments(
      @Param("farmId") Long farmId, @Param("issueId") Long issueId);

  int insertIssue(IssueRow row);

  int updateStatus(
      @Param("farmId") Long farmId,
      @Param("id") Long id,
      @Param("status") String status,
      @Param("version") Long version,
      @Param("userId") Long userId,
      @Param("now") LocalDateTime now);

  List<FollowupRow> findFollowups(
      @Param("farmId") Long farmId,
      @Param("issueId") Long issueId,
      @Param("offset") int offset,
      @Param("size") int size);

  long countFollowups(@Param("farmId") Long farmId, @Param("issueId") Long issueId);

  Optional<FollowupRow> findFollowupByRequest(
      @Param("issueId") Long issueId, @Param("clientRequestId") String requestId);

  Optional<FollowupRow> findFollowupByRequestForUpdate(
      @Param("issueId") Long issueId, @Param("clientRequestId") String requestId);

  Optional<FollowupRow> findFollowupById(@Param("farmId") Long farmId, @Param("id") Long id);

  int insertFollowup(FollowupRow row);

  void insertAudit(
      @Param("organizationId") Long organizationId,
      @Param("farmId") Long farmId,
      @Param("actorUserId") Long actor,
      @Param("action") String action,
      @Param("targetType") String targetType,
      @Param("targetId") Long targetId,
      @Param("detailJson") String detail,
      @Param("now") LocalDateTime now);
}
