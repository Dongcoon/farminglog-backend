package com.farmlog.farmaccess;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.entity.FarmMemberEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmaccess.dto.*;
import com.farmlog.farmaccess.entity.*;
import com.farmlog.farmaccess.mapper.FarmAccessMapper;
import com.farmlog.records.dto.FeedResponse;
import com.farmlog.records.dto.PageResponse;
import com.farmlog.records.dto.RecordResponse;
import com.farmlog.user.entity.UserEntity;
import com.farmlog.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class FarmAccessService {
  private static final Logger log = LoggerFactory.getLogger(FarmAccessService.class);
  private static final Set<String> MEMBER_ROLES =
      Set.of("FARM_OWNER", "FARM_MANAGER", "WORKER", "VIEWER");
  private static final Set<String> INVITE_ROLES = Set.of("FARM_MANAGER", "WORKER", "VIEWER");
  private final FarmAccessMapper mapper;
  private final FarmAccessGuard farmGuard;
  private final FarmMapper farmMapper;
  private final UserMapper userMapper;
  private final ObjectMapper json;
  private final SecureRandom random = new SecureRandom();
  private final InvitationDelivery invitationDelivery;
  private final FarmMutationGuard mutationGuard;

  @Autowired
  public FarmAccessService(
      FarmAccessMapper mapper,
      FarmAccessGuard farmGuard,
      FarmMapper farmMapper,
      UserMapper userMapper,
      ObjectMapper json,
      InvitationDelivery invitationDelivery,
      FarmMutationGuard mutationGuard) {
    this.mapper = mapper;
    this.farmGuard = farmGuard;
    this.farmMapper = farmMapper;
    this.userMapper = userMapper;
    this.json = json;
    this.invitationDelivery = invitationDelivery;
    this.mutationGuard = mutationGuard;
  }

  public FarmAccessService(
      FarmAccessMapper mapper,
      FarmAccessGuard farmGuard,
      FarmMapper farmMapper,
      UserMapper userMapper,
      ObjectMapper json,
      InvitationDelivery invitationDelivery) {
    this(mapper, farmGuard, farmMapper, userMapper, json, invitationDelivery,
        new FarmMutationGuard(farmMapper));
  }

  public PageResponse<MemberDto> members(Long userId, Long farmId, int page, int size) {
    owner(userId, farmId);
    page(page, size);
    return PageResponse.of(
        mapper.findMembers(farmId, page * size, size).stream().map(r -> member(r, true)).toList(),
        page,
        size,
        mapper.countMembers(farmId));
  }

  @Transactional
  public MemberDto updateMember(Long userId, Long farmId, Long id, MemberUpdateRequest req) {
    owner(userId, farmId);
    mutationGuard.lockActiveStructureFarm(farmId);
    ownerForUpdate(userId, farmId);
    if (req == null || req.version() == null) throw validation("version은 필수입니다.");
    List<Long> lockedOwners = mapper.lockActiveOwners(farmId);
    MemberRow before =
        mapper
            .findMember(farmId, id)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_MEMBER_NOT_FOUND));
    String role = req.role() == null ? before.getRole() : req.role();
    String status = req.status() == null ? before.getStatus() : req.status();
    if (!MEMBER_ROLES.contains(role) || !Set.of("ACTIVE", "INACTIVE").contains(status))
      throw validation("역할 또는 상태가 올바르지 않습니다.");
    if (Objects.equals(role, before.getRole()) && Objects.equals(status, before.getStatus())) {
      return member(before, true);
    }
    if ("FARM_OWNER".equals(before.getRole())
        && "ACTIVE".equals(before.getStatus())
        && (!"FARM_OWNER".equals(role) || !"ACTIVE".equals(status))
        && lockedOwners.size() <= 1)
      throw new BusinessException(ErrorCode.CONFLICT, "마지막 농장주는 강등하거나 비활성화할 수 없습니다.");
    if (mapper.updateMember(farmId, id, role, status, req.version(), LocalDateTime.now()) != 1)
      throw conflict();
    mutationGuard.incrementStructureVersion(farmId, userId);
    MemberRow after = mapper.findMember(farmId, id).orElseThrow();
    audit(
        farmId,
        userId,
        "FARM_MEMBER_UPDATED",
        "FARM_MEMBER",
        id,
        Map.of(
            "beforeRole",
            before.getRole(),
            "afterRole",
            role,
            "beforeStatus",
            before.getStatus(),
            "afterStatus",
            status));
    return member(after, true);
  }

  public PageResponse<InvitationDto> invitations(
      Long userId, Long farmId, String status, int page, int size) {
    owner(userId, farmId);
    page(page, size);
    if (status != null && !Set.of("PENDING", "ACCEPTED", "CANCELLED", "EXPIRED").contains(status))
      throw validation("초대 상태가 올바르지 않습니다.");
    LocalDateTime now = LocalDateTime.now();
    return PageResponse.of(
        mapper.findInvitations(farmId, status, page * size, size, now).stream()
            .map(this::invite)
            .toList(),
        page,
        size,
        mapper.countInvitations(farmId, status, now));
  }

  @Transactional
  public InvitationDto createInvitation(Long userId, Long farmId, InvitationRequests.Create req) {
    owner(userId, farmId);
    FarmEntity lockedFarm = mutationGuard.lockActiveFarm(farmId);
    ownerForUpdate(userId, farmId);
    uuid(req.clientRequestId());
    if (!INVITE_ROLES.contains(req.role())) throw validation("초대 역할이 올바르지 않습니다.");
    String email = normalize(req.email());
    LocalDateTime now = LocalDateTime.now();
    var existing = mapper.findInvitationByRequest(farmId, req.clientRequestId(), now);
    if (existing.isPresent()) return invite(existing.get());
    if (mapper.findMemberByEmail(farmId, email).isPresent())
      throw new BusinessException(ErrorCode.CONFLICT, "이미 이 농장의 구성원입니다.");
    byte[] tokenBytes = new byte[32];
    random.nextBytes(tokenBytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    InvitationRow row = new InvitationRow();
    row.setOrganizationId(lockedFarm.getOrganizationId());
    row.setFarmId(farmId);
    row.setEmail(email);
    row.setRole(req.role());
    row.setInviteTokenHash(hash(rawToken));
    row.setClientRequestId(req.clientRequestId());
    row.setInvitedBy(userId);
    row.setInvitedAt(now);
    row.setExpiresAt(now.plusDays(7));
    row.setVersion(0L);
    try {
      mapper.insertInvitation(row);
    } catch (DuplicateKeyException ex) {
      return invite(
          mapper
              .findInvitationByRequestForUpdate(farmId, req.clientRequestId(), now)
              .orElseThrow(() -> ex));
    }
    audit(
        farmId,
        userId,
        "FARM_INVITATION_CREATED",
        "FARM_INVITATION",
        row.getId(),
        Map.of("role", req.role()));
    afterCommit(() -> deliverSafely(email, rawToken));
    return invite(mapper.findInvitation(farmId, row.getId(), now).orElse(row));
  }

  @Transactional
  public InvitationDto cancelInvitation(
      Long userId, Long farmId, Long id, InvitationRequests.Cancel req) {
    owner(userId, farmId);
    mutationGuard.lockActiveFarm(farmId);
    ownerForUpdate(userId, farmId);
    if (req == null || !"CANCELLED".equals(req.status()) || req.version() == null)
      throw validation("status=CANCELLED와 version이 필요합니다.");
    LocalDateTime now = LocalDateTime.now();
    mapper
        .findInvitation(farmId, id, now)
        .orElseThrow(() -> new BusinessException(ErrorCode.FARM_INVITATION_NOT_FOUND));
    if (mapper.cancelInvitation(farmId, id, req.version(), now) != 1) throw conflict();
    audit(
        farmId,
        userId,
        "FARM_INVITATION_CANCELLED",
        "FARM_INVITATION",
        id,
        Map.of("version", req.version()));
    return invite(mapper.findInvitation(farmId, id, now).orElseThrow());
  }

  @Transactional
  public MemberDto acceptInvitation(Long userId, String userEmail, String rawToken) {
    if (!StringUtils.hasText(rawToken))
      throw new BusinessException(ErrorCode.FARM_INVITATION_INVALID);
    LocalDateTime now = LocalDateTime.now();
    String tokenHash = hash(rawToken);
    InvitationRow candidate =
        mapper
            .findInvitationByToken(tokenHash, now)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_INVITATION_NOT_FOUND));
    if (!normalize(userEmail).equals(candidate.getEmail()))
      throw new BusinessException(ErrorCode.FORBIDDEN, "초대 이메일과 로그인 이메일이 다릅니다.");
    // 토큰은 farmId 탐색에만 사용하고, 부모 farm 잠금 뒤 초대 행을 다시 잠가 검증한다.
    mutationGuard.lockActiveStructureFarm(candidate.getFarmId());
    InvitationRow invitation =
        mapper
            .findInvitationByTokenForUpdate(tokenHash, now)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_INVITATION_NOT_FOUND));
    if (!normalize(userEmail).equals(invitation.getEmail()))
      throw new BusinessException(ErrorCode.FORBIDDEN, "초대 이메일과 로그인 이메일이 다릅니다.");
    Optional<MemberRow> current = mapper.findMemberByUserForUpdate(invitation.getFarmId(), userId);
    if ("ACCEPTED".equals(invitation.getStatus())
        && Objects.equals(invitation.getAcceptedUserId(), userId))
      return member(current.orElseThrow(), false);
    if ("EXPIRED".equals(invitation.getStatus()))
      throw new BusinessException(ErrorCode.FARM_INVITATION_EXPIRED);
    if (!"PENDING".equals(invitation.getStatus()))
      throw new BusinessException(ErrorCode.FARM_INVITATION_INVALID);
    if (current.isPresent()) throw new BusinessException(ErrorCode.CONFLICT, "이미 이 농장의 구성원입니다.");
    mapper.upsertAcceptedMember(invitation.getFarmId(), userId, invitation.getRole(), now);
    if (mapper.acceptInvitation(invitation.getId(), invitation.getVersion(), userId, now) != 1)
      throw conflict();
    mutationGuard.incrementStructureVersion(invitation.getFarmId(), userId);
    audit(
        invitation.getFarmId(),
        userId,
        "FARM_INVITATION_ACCEPTED",
        "FARM_INVITATION",
        invitation.getId(),
        Map.of("role", invitation.getRole()));
    return member(
        mapper.findMemberByUserForUpdate(invitation.getFarmId(), userId).orElseThrow(), false);
  }

  public PageResponse<CareAssignmentDto> farmAssignments(
      Long userId, Long farmId, String status, int page, int size) {
    owner(userId, farmId);
    page(page, size);
    careStatus(status);
    LocalDateTime now = LocalDateTime.now();
    return assignmentPage(
        mapper.findFarmAssignments(farmId, status, page * size, size, now),
        page,
        size,
        mapper.countFarmAssignments(farmId, status, now),
        true);
  }

  public PageResponse<CareAssignmentDto> myAssignments(
      Long userId, String status, int page, int size) {
    page(page, size);
    careStatus(status);
    LocalDateTime now = LocalDateTime.now();
    return assignmentPage(
        mapper.findManagerAssignments(userId, status, page * size, size, now),
        page,
        size,
        mapper.countManagerAssignments(userId, status, now),
        false);
  }

  public List<FeedResponse> recentRecords(Long userId, Long farmId, Long assignmentId, int size) {
    owner(userId, farmId);
    if (size < 1 || size > 20) throw validation("size는 1~20입니다.");
    mapper
        .findAssignment(farmId, assignmentId, LocalDateTime.now())
        .orElseThrow(() -> new BusinessException(ErrorCode.CARE_ASSIGNMENT_NOT_FOUND));
    return mapper.findRecentRecords(farmId, assignmentId, size).stream()
        .map(
            r ->
                new FeedResponse(
                    r.getType(),
                    r.getId(),
                    r.getRecordDate(),
                    r.getZoneId() == null
                        ? null
                        : new RecordResponse.Reference(
                            r.getZoneId(), r.getZoneName(), "Y".equals(r.getZoneActiveYn())),
                    r.getPrimaryLabel(),
                    r.getPrimaryValue(),
                    new RecordResponse.Actor(r.getCreatedBy(), r.getCreatedByName()),
                    r.getCreatedRole(),
                    true,
                    r.getFarmerConfirmStatus(),
                    r.getCreatedAt(),
                    true,
                    true))
        .toList();
  }

  @Transactional
  public CareAssignmentDto createAssignment(
      Long userId, Long farmId, CareAssignmentRequests.Create req) {
    owner(userId, farmId);
    FarmEntity lockedFarm = mutationGuard.lockActiveFarm(farmId);
    ownerForUpdate(userId, farmId);
    uuid(req.clientRequestId());
    LocalDateTime now = LocalDateTime.now();
    var found = mapper.findAssignmentByRequest(farmId, req.clientRequestId(), now);
    if (found.isPresent()) return care(found.get(), true);
    UserEntity manager =
        userMapper
            .findByEmail(normalize(req.managerEmail()))
            .filter(UserEntity::isActive)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    LocalDateTime starts = req.startsAt() == null ? now : req.startsAt();
    if (req.endsAt() != null && !req.endsAt().isAfter(starts))
      throw validation("종료 시각은 시작 시각보다 늦어야 합니다.");
    // 부모 행 잠금으로 겹치는 배정의 동시 생성을 직렬화하고, 잠금 뒤 멱등 요청을 다시 읽는다.
    mapper
        .lockAssignmentScope(farmId, manager.getId())
        .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    var serialized = mapper.findAssignmentByRequest(farmId, req.clientRequestId(), now);
    if (serialized.isPresent()) return care(serialized.get(), true);
    if (mapper.countOverlappingAssignments(farmId, manager.getId(), starts, req.endsAt()) > 0)
      throw new BusinessException(ErrorCode.CONFLICT, "같은 기간에 이미 활성 배정이 있습니다.");
    CareAssignmentRow row = new CareAssignmentRow();
    row.setOrganizationId(lockedFarm.getOrganizationId());
    row.setFarmId(farmId);
    row.setManagerUserId(manager.getId());
    row.setAssignedBy(userId);
    row.setAssignmentType("DATA_CARE");
    row.setPermissionScope("REVIEW_AND_INPUT");
    row.setStatus("ACTIVE");
    row.setStartsAt(starts);
    row.setEndsAt(req.endsAt());
    row.setMemo(trim(req.memo(), 2000));
    row.setClientRequestId(req.clientRequestId());
    row.setAssignedAt(now);
    row.setVersion(0L);
    try {
      mapper.insertAssignment(row);
    } catch (DuplicateKeyException ex) {
      return care(
          mapper
              .findAssignmentByRequestForUpdate(farmId, req.clientRequestId(), now)
              .orElseThrow(() -> ex),
          true);
    }
    audit(
        farmId,
        userId,
        "CARE_ASSIGNMENT_CREATED",
        "FARM_CARE_ASSIGNMENT",
        row.getId(),
        Map.of("managerUserId", manager.getId()));
    return care(mapper.findAssignment(farmId, row.getId(), now).orElse(row), true);
  }

  @Transactional
  public CareAssignmentDto revokeAssignment(
      Long userId, Long farmId, Long id, CareAssignmentRequests.Revoke req) {
    owner(userId, farmId);
    mutationGuard.lockActiveFarm(farmId);
    ownerForUpdate(userId, farmId);
    if (req == null || !"REVOKED".equals(req.status()) || req.version() == null)
      throw validation("status=REVOKED와 version이 필요합니다.");
    CareAssignmentRow row =
        mapper
            .findAssignment(farmId, id, LocalDateTime.now())
            .orElseThrow(() -> new BusinessException(ErrorCode.CARE_ASSIGNMENT_NOT_FOUND));
    if (!"ACTIVE".equals(row.getStatus()))
      throw new BusinessException(ErrorCode.CONFLICT, "현재 활성 배정만 회수할 수 있습니다.");
    if (mapper.revokeAssignment(
            farmId, id, req.version(), userId, trim(req.reason(), 500), LocalDateTime.now())
        != 1) throw conflict();
    audit(
        farmId,
        userId,
        "CARE_ASSIGNMENT_REVOKED",
        "FARM_CARE_ASSIGNMENT",
        id,
        Map.of("managerUserId", row.getManagerUserId()));
    return care(mapper.findAssignment(farmId, id, LocalDateTime.now()).orElseThrow(), true);
  }

  private PageResponse<CareAssignmentDto> assignmentPage(
      List<CareAssignmentRow> rows, int page, int size, long total, boolean owner) {
    page(page, size);
    return PageResponse.of(rows.stream().map(r -> care(r, owner)).toList(), page, size, total);
  }

  private MemberDto member(MemberRow r, boolean owner) {
    boolean last =
        "FARM_OWNER".equals(r.getRole())
            && "ACTIVE".equals(r.getStatus())
            && mapper.countActiveOwners(r.getFarmId()) <= 1;
    return new MemberDto(
        r.getId(),
        r.getFarmId(),
        new MemberDto.User(r.getUserId(), r.getEmail(), r.getDisplayName()),
        r.getRole(),
        r.getStatus(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        r.getVersion(),
        owner && !last,
        owner && !last);
  }

  private InvitationDto invite(InvitationRow r) {
    return new InvitationDto(
        r.getId(),
        r.getFarmId(),
        r.getEmail(),
        r.getRole(),
        r.getStatus(),
        new InvitationDto.Actor(r.getInvitedBy(), r.getInvitedByName()),
        r.getInvitedAt(),
        r.getExpiresAt(),
        r.getRespondedAt(),
        r.getVersion(),
        "PENDING".equals(r.getStatus()));
  }

  private CareAssignmentDto care(CareAssignmentRow r, boolean owner) {
    return new CareAssignmentDto(
        r.getId(),
        r.getVersion(),
        new CareAssignmentDto.Farm(r.getFarmId(), r.getFarmName()),
        new CareAssignmentDto.Manager(
            r.getManagerUserId(), r.getManagerName(), r.getManagerEmail()),
        new CareAssignmentDto.Actor(r.getAssignedBy(), r.getAssignedByName()),
        r.getAssignmentType(),
        r.getPermissionScope(),
        r.getStatus(),
        r.getStartsAt(),
        r.getEndsAt(),
        r.getAssignedAt(),
        r.getRevokedAt(),
        r.getRevokedBy() == null
            ? null
            : new CareAssignmentDto.Actor(r.getRevokedBy(), r.getRevokedByName()),
        r.getRevokedReason(),
        r.getMemo(),
        r.getRecentInputCount(),
        r.getLastInputAt(),
        r.getOpenIssueCount(),
        owner && "ACTIVE".equals(r.getStatus()));
  }

  private void owner(Long userId, Long farmId) {
    farmGuard.requireFarmRole(userId, farmId, FarmMemberEntity.ROLE_FARM_OWNER);
  }

  private void ownerForUpdate(Long userId, Long farmId) {
    farmGuard.requireFarmRoleForUpdate(userId, farmId, FarmMemberEntity.ROLE_FARM_OWNER);
  }

  private void page(int p, int s) {
    if (p < 0 || s < 1 || s > 100 || (long) p * s > Integer.MAX_VALUE)
      throw validation("page는 0 이상, size는 1~100입니다.");
  }

  private void careStatus(String status) {
    if (status != null && !Set.of("ACTIVE", "REVOKED", "EXPIRED").contains(status))
      throw validation("배정 상태가 올바르지 않습니다.");
  }

  private void uuid(String v) {
    try {
      UUID.fromString(v);
    } catch (Exception e) {
      throw validation("clientRequestId는 UUID 형식이어야 합니다.");
    }
  }

  private String normalize(String v) {
    return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
  }

  private String trim(String v, int max) {
    if (v == null) return null;
    String x = v.trim();
    if (x.length() > max) throw validation("입력값이 너무 깁니다.");
    return x.isEmpty() ? null : x;
  }

  private String hash(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void audit(
      Long farmId, Long actor, String action, String target, Long id, Map<String, Object> detail) {
    FarmEntity farm =
        farmMapper
            .findById(farmId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    try {
      mapper.insertAudit(
          farm.getOrganizationId(),
          farmId,
          actor,
          action,
          target,
          id,
          json.writeValueAsString(detail),
          LocalDateTime.now());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private void deliverSafely(String email, String rawToken) {
    try {
      invitationDelivery.deliver(email, rawToken);
    } catch (RuntimeException ex) {
      log.error(
          "Invitation delivery failed after commit for {}. Configure SMTP/outbox retry in"
              + " production.",
          email,
          ex);
    }
  }

  private BusinessException validation(String m) {
    return new BusinessException(ErrorCode.VALIDATION_FAILED, m);
  }

  private BusinessException conflict() {
    return new BusinessException(ErrorCode.CONFLICT, "다른 요청이 먼저 변경했습니다. 새로고침 후 다시 시도해 주세요.");
  }
}
