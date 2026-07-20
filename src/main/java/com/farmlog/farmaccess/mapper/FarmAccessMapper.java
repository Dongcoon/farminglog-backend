package com.farmlog.farmaccess.mapper;

import com.farmlog.farmaccess.entity.*;
import com.farmlog.records.entity.FeedRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FarmAccessMapper {
  List<MemberRow> findMembers(
      @Param("farmId") Long farmId, @Param("offset") int offset, @Param("size") int size);

  long countMembers(@Param("farmId") Long farmId);

  Optional<MemberRow> findMember(@Param("farmId") Long farmId, @Param("id") Long id);

  long countActiveOwners(@Param("farmId") Long farmId);

  List<Long> lockActiveOwners(@Param("farmId") Long farmId);

  Optional<MemberRow> findMemberByEmail(@Param("farmId") Long farmId, @Param("email") String email);

  Optional<MemberRow> findMemberByUserForUpdate(
      @Param("farmId") Long farmId, @Param("userId") Long userId);

  int updateMember(
      @Param("farmId") Long farmId,
      @Param("id") Long id,
      @Param("role") String role,
      @Param("status") String status,
      @Param("version") Long version,
      @Param("now") LocalDateTime now);

  List<InvitationRow> findInvitations(
      @Param("farmId") Long farmId,
      @Param("status") String status,
      @Param("offset") int offset,
      @Param("size") int size,
      @Param("now") LocalDateTime now);

  long countInvitations(
      @Param("farmId") Long farmId,
      @Param("status") String status,
      @Param("now") LocalDateTime now);

  Optional<InvitationRow> findInvitation(
      @Param("farmId") Long farmId, @Param("id") Long id, @Param("now") LocalDateTime now);

  Optional<InvitationRow> findInvitationByRequest(
      @Param("farmId") Long farmId,
      @Param("clientRequestId") String requestId,
      @Param("now") LocalDateTime now);

  Optional<InvitationRow> findInvitationByRequestForUpdate(
      @Param("farmId") Long farmId,
      @Param("clientRequestId") String requestId,
      @Param("now") LocalDateTime now);

  Optional<InvitationRow> findInvitationByTokenForUpdate(
      @Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

  Optional<InvitationRow> findInvitationByToken(
      @Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

  int insertInvitation(InvitationRow row);

  int cancelInvitation(
      @Param("farmId") Long farmId,
      @Param("id") Long id,
      @Param("version") Long version,
      @Param("now") LocalDateTime now);

  int acceptInvitation(
      @Param("id") Long id,
      @Param("version") Long version,
      @Param("userId") Long userId,
      @Param("now") LocalDateTime now);

  int upsertAcceptedMember(
      @Param("farmId") Long farmId,
      @Param("userId") Long userId,
      @Param("role") String role,
      @Param("now") LocalDateTime now);

  List<CareAssignmentRow> findFarmAssignments(
      @Param("farmId") Long farmId,
      @Param("status") String status,
      @Param("offset") int offset,
      @Param("size") int size,
      @Param("now") LocalDateTime now);

  long countFarmAssignments(
      @Param("farmId") Long farmId,
      @Param("status") String status,
      @Param("now") LocalDateTime now);

  List<CareAssignmentRow> findManagerAssignments(
      @Param("userId") Long userId,
      @Param("status") String status,
      @Param("offset") int offset,
      @Param("size") int size,
      @Param("now") LocalDateTime now);

  long countManagerAssignments(
      @Param("userId") Long userId,
      @Param("status") String status,
      @Param("now") LocalDateTime now);

  Optional<CareAssignmentRow> findAssignment(
      @Param("farmId") Long farmId, @Param("id") Long id, @Param("now") LocalDateTime now);

  Optional<CareAssignmentRow> findActiveAssignment(
      @Param("farmId") Long farmId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

  Optional<CareAssignmentRow> findActiveAssignmentForUpdate(
      @Param("farmId") Long farmId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

  Optional<CareAssignmentRow> findAssignmentByRequest(
      @Param("farmId") Long farmId,
      @Param("clientRequestId") String requestId,
      @Param("now") LocalDateTime now);

  Optional<CareAssignmentRow> findAssignmentByRequestForUpdate(
      @Param("farmId") Long farmId,
      @Param("clientRequestId") String requestId,
      @Param("now") LocalDateTime now);

  Optional<Long> lockAssignmentScope(
      @Param("farmId") Long farmId, @Param("managerUserId") Long managerUserId);

  long countOverlappingAssignments(
      @Param("farmId") Long farmId,
      @Param("managerUserId") Long managerUserId,
      @Param("startsAt") LocalDateTime startsAt,
      @Param("endsAt") LocalDateTime endsAt);

  int insertAssignment(CareAssignmentRow row);

  int revokeAssignment(
      @Param("farmId") Long farmId,
      @Param("id") Long id,
      @Param("version") Long version,
      @Param("userId") Long userId,
      @Param("reason") String reason,
      @Param("now") LocalDateTime now);

  List<FeedRow> findRecentRecords(
      @Param("farmId") Long farmId,
      @Param("assignmentId") Long assignmentId,
      @Param("size") int size);

  void insertAudit(
      @Param("organizationId") Long organizationId,
      @Param("farmId") Long farmId,
      @Param("actorUserId") Long actorUserId,
      @Param("action") String action,
      @Param("targetType") String targetType,
      @Param("targetId") Long targetId,
      @Param("detailJson") String detailJson,
      @Param("now") LocalDateTime now);
}
