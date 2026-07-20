package com.farmlog.dataquality;

import com.farmlog.common.exception.*;
import com.farmlog.common.tenant.*;
import com.farmlog.dataquality.dto.*;
import com.farmlog.dataquality.entity.*;
import com.farmlog.dataquality.mapper.DataQualityMapper;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmaccess.*;
import com.farmlog.farmaccess.entity.CareAssignmentRow;
import com.farmlog.records.RecordType;
import com.farmlog.records.dto.PageResponse;
import com.farmlog.records.mapper.RecordMapper;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataQualityService {
  public static final Set<String> TYPES =
      Set.of(
          "PHOTO_ONLY",
          "HARVEST_WITHOUT_SALES",
          "PEST_DETAILS_MISSING",
          "SALES_DETAILS_MISSING",
          "INACTIVITY",
          "OTHER");
  public static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
  public static final Set<String> SEVERITIES = Set.of("LOW", "NORMAL", "HIGH");
  private static final Set<String> CONFIRM =
      Set.of("NOT_REQUIRED", "PENDING", "CONFIRMED", "CHANGES_REQUESTED");
  private static final Set<String> CONTACT =
      Set.of("PHONE", "SMS", "KAKAO", "APP_NOTIFICATION", "OTHER");
  private static final Set<String> RESULTS =
      Set.of("CONTACTED", "NO_ANSWER", "ACTION_REQUIRED", "COMPLETED");
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final DataQualityMapper mapper;
  private final FarmAccessGuard farmGuard;
  private final CareAccessGuard careGuard;
  private final FarmMapper farmMapper;
  private final RecordMapper recordMapper;
  private final FarmMutationGuard mutationGuard;

  @Autowired
  public DataQualityService(
      DataQualityMapper mapper,
      FarmAccessGuard farmGuard,
      CareAccessGuard careGuard,
      FarmMapper farmMapper,
      RecordMapper recordMapper,
      FarmMutationGuard mutationGuard) {
    this.mapper = mapper;
    this.farmGuard = farmGuard;
    this.careGuard = careGuard;
    this.farmMapper = farmMapper;
    this.recordMapper = recordMapper;
    this.mutationGuard = mutationGuard;
  }

  public DataQualityService(
      DataQualityMapper mapper,
      FarmAccessGuard farmGuard,
      CareAccessGuard careGuard,
      FarmMapper farmMapper,
      RecordMapper recordMapper) {
    this(mapper, farmGuard, careGuard, farmMapper, recordMapper,
        new FarmMutationGuard(farmMapper));
  }

  public PageResponse<DataQualityIssueDto> list(
      Long user,
      Long farm,
      String type,
      String status,
      String severity,
      String confirm,
      int page,
      int size,
      String sort) {
    Access a = access(user, farm);
    validateFilters(type, status, severity, confirm);
    page(page, size);
    Sort s = sort(sort);
    List<DataQualityIssueDto> content =
        mapper
            .findPage(
                farm, type, status, severity, confirm, s.column, s.direction, page * size, size)
            .stream()
            .map(r -> dto(r, a, false))
            .toList();
    return PageResponse.of(
        content, page, size, mapper.countPage(farm, type, status, severity, confirm));
  }

  public DataQualityIssueDto detail(Long user, Long farm, Long id) {
    Access a = access(user, farm);
    return dto(get(farm, id), a, true);
  }

  @Transactional
  public DataQualityIssueDto create(Long user, Long farm, IssueRequests.Create req) {
    Access a = accessForWrite(user, farm);
    if (!a.canResolve) throw new BusinessException(ErrorCode.FORBIDDEN);
    String severity = req == null ? null : (req.severity() == null ? "NORMAL" : req.severity());
    if (req == null
        || req.issueDate() == null
        || req.title() == null
        || req.title().isBlank()
        || !TYPES.contains(req.issueType())
        || !SEVERITIES.contains(severity)) throw validation("이슈 유형 또는 심각도가 올바르지 않습니다.");
    if (req.issueDate().isAfter(LocalDate.now(SEOUL))) throw validation("미래 날짜는 사용할 수 없습니다.");
    validateResource(farm, req.zoneId(), req.relatedDomain(), req.relatedRecordId());
    FarmEntity f =
        farmMapper
            .findById(farm)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    IssueRow row = new IssueRow();
    row.setOrganizationId(f.getOrganizationId());
    row.setFarmId(farm);
    row.setZoneId(req.zoneId());
    row.setIssueDate(req.issueDate());
    row.setIssueType(req.issueType());
    row.setIssueStatus("OPEN");
    row.setSeverity(severity);
    row.setTitle(req.title().trim());
    row.setDescription(trim(req.description(), 4000));
    row.setDetectedBy(a.care ? "FARM_CARE_MANAGER" : "FARM_MEMBER");
    row.setAssignedManagerUserId(a.care ? user : null);
    row.setRelatedRefType(refType(req.relatedDomain()));
    row.setRelatedRefId(req.relatedRecordId());
    row.setFarmerConfirmStatus("NOT_REQUIRED");
    row.setCreatedBy(user);
    row.setCreatedAt(LocalDateTime.now());
    row.setVersion(0L);
    mapper.insertIssue(row);
    audit(
        row, user, "DATA_QUALITY_ISSUE_CREATED", "{\"issueType\":\"" + row.getIssueType() + "\"}");
    return dto(get(farm, row.getId()), a, true);
  }

  /** photo-only commit과 같은 트랜잭션에서 이슈를 생성한다. */
  public IssueRow createPhotoOnly(
      Long user, Long farm, Long zoneId, LocalDate date, String estimatedType, String memo) {
    Access a = accessForWrite(user, farm);
    if (date == null || date.isAfter(LocalDate.now(SEOUL))) throw validation("미래 날짜는 사용할 수 없습니다.");
    validateResource(farm, zoneId, null, null);
    FarmEntity f =
        farmMapper
            .findById(farm)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    IssueRow row = new IssueRow();
    row.setOrganizationId(f.getOrganizationId());
    row.setFarmId(farm);
    row.setZoneId(zoneId);
    row.setIssueDate(date);
    row.setIssueType("PHOTO_ONLY");
    row.setIssueStatus("OPEN");
    row.setSeverity("NORMAL");
    row.setTitle("사진만 저장 · " + estimatedType);
    row.setDescription(trim(memo, 4000));
    row.setDetectedBy(a.care ? "FARM_CARE_MANAGER" : "FARM_MEMBER");
    row.setAssignedManagerUserId(a.care ? user : null);
    row.setFarmerConfirmStatus("NOT_REQUIRED");
    row.setCreatedBy(user);
    row.setCreatedAt(LocalDateTime.now());
    row.setVersion(0L);
    mapper.insertIssue(row);
    return row;
  }

  @Transactional
  public DataQualityIssueDto update(Long user, Long farm, Long id, IssueRequests.Update req) {
    Access a = accessForWrite(user, farm);
    if (!a.canResolve) throw new BusinessException(ErrorCode.FORBIDDEN);
    if (req == null || req.version() == null || !STATUSES.contains(req.issueStatus()))
      throw validation("issueStatus와 version을 확인해 주세요.");
    IssueRow row = get(farm, id);
    if (mapper.updateStatus(farm, id, req.issueStatus(), req.version(), user, LocalDateTime.now())
        != 1) throw conflict();
    audit(
        row,
        user,
        "DATA_QUALITY_STATUS_CHANGED",
        "{\"before\":\"" + row.getIssueStatus() + "\",\"after\":\"" + req.issueStatus() + "\"}");
    return dto(get(farm, id), a, true);
  }

  public PageResponse<FollowupDto> followups(Long user, Long farm, Long issue, int page, int size) {
    access(user, farm);
    get(farm, issue);
    page(page, size);
    return PageResponse.of(
        mapper.findFollowups(farm, issue, page * size, size).stream().map(this::followup).toList(),
        page,
        size,
        mapper.countFollowups(farm, issue));
  }

  @Transactional
  public FollowupDto createFollowup(Long user, Long farm, Long issue, FollowupCreateRequest req) {
    Access a = accessForWrite(user, farm);
    if (!a.canResolve) throw new BusinessException(ErrorCode.FORBIDDEN);
    IssueRow parent = get(farm, issue);
    uuid(req.clientRequestId());
    if (!CONTACT.contains(req.contactMethod()) || !RESULTS.contains(req.followupStatus()))
      throw validation("연락 수단 또는 결과가 올바르지 않습니다.");
    var existing = mapper.findFollowupByRequest(issue, req.clientRequestId());
    if (existing.isPresent()) return followup(existing.get());
    FollowupRow row = new FollowupRow();
    row.setOrganizationId(parent.getOrganizationId());
    row.setFarmId(farm);
    row.setIssueId(issue);
    row.setManagerUserId(user);
    row.setContactMethod(req.contactMethod());
    row.setFollowupStatus(req.followupStatus());
    row.setContentSummary(req.contentSummary().trim());
    row.setNextActionAt(req.nextActionAt());
    row.setClientRequestId(req.clientRequestId());
    row.setCreatedAt(LocalDateTime.now());
    try {
      mapper.insertFollowup(row);
    } catch (DuplicateKeyException ex) {
      return followup(
          mapper
              .findFollowupByRequestForUpdate(issue, req.clientRequestId())
              .orElseThrow(() -> ex));
    }
    audit(
        parent,
        user,
        "DATA_QUALITY_FOLLOWUP_CREATED",
        "{\"contactMethod\":\""
            + row.getContactMethod()
            + "\",\"result\":\""
            + row.getFollowupStatus()
            + "\"}");
    return followup(mapper.findFollowupById(farm, row.getId()).orElseThrow());
  }

  private DataQualityIssueDto dto(IssueRow r, Access a, boolean attachments) {
    return new DataQualityIssueDto(
        r.getId(),
        r.getFarmId(),
        r.getFarmName(),
        r.getZoneId() == null
            ? null
            : new DataQualityIssueDto.Reference(r.getZoneId(), r.getZoneName()),
        r.getIssueDate(),
        r.getIssueType(),
        r.getIssueStatus(),
        r.getSeverity(),
        r.getTitle(),
        r.getDescription(),
        r.getDetectedBy(),
        related(r.getRelatedRefType(), r.getRelatedRefId()),
        related(r.getResolutionRefType(), r.getResolutionRefId()),
        r.getFarmerConfirmStatus(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        r.getClosedAt(),
        r.getVersion(),
        a.canEdit,
        a.canResolve,
        attachments
            ? mapper.findIssueAttachments(r.getFarmId(), r.getId()).stream()
                .map(
                    x ->
                        new DataQualityIssueDto.AttachmentSummary(
                            x.getId(),
                            x.getOriginalFileName(),
                            x.getContentType(),
                            x.getFileSize(),
                            x.getCreatedAt()))
                .toList()
            : null);
  }

  private DataQualityIssueDto.RelatedRecord related(String type, Long id) {
    if (type == null || id == null) return null;
    String domain =
        switch (type) {
          case "WORK" -> "work-logs";
          case "PEST_CONTROL" -> "pest-control-logs";
          case "HARVEST" -> "harvest-logs";
          case "SALES" -> "sales-logs";
          default -> null;
        };
    return domain == null
        ? null
        : new DataQualityIssueDto.RelatedRecord(domain, id, type + " #" + id, null);
  }

  private String refType(String domain) {
    if (domain == null) return null;
    return switch (domain) {
      case "work-logs" -> "WORK";
      case "pest-control-logs" -> "PEST_CONTROL";
      case "harvest-logs" -> "HARVEST";
      case "sales-logs" -> "SALES";
      default -> throw validation("relatedDomain이 올바르지 않습니다.");
    };
  }

  private FollowupDto followup(FollowupRow r) {
    return new FollowupDto(
        r.getId(),
        r.getIssueId(),
        new FollowupDto.Actor(r.getManagerUserId(), r.getManagerName()),
        r.getContactMethod(),
        r.getFollowupStatus(),
        r.getContentSummary(),
        r.getNextActionAt(),
        r.getCreatedAt());
  }

  private IssueRow get(Long farm, Long id) {
    return mapper
        .findById(farm, id)
        .orElseThrow(() -> new BusinessException(ErrorCode.DATA_QUALITY_ISSUE_NOT_FOUND));
  }

  private Access access(Long user, Long farm) {
    FarmMembership membership;
    try {
      membership = farmGuard.requireFarmMember(user, farm);
    } catch (BusinessException ex) {
      if (ex.getErrorCode() != ErrorCode.FORBIDDEN) throw ex;
      CareAssignmentRow assignment = careGuard.requireActive(user, farm);
      return new Access(false, true, true, assignment);
    }
    if ("FARM_CARE_MANAGER".equals(membership.role()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    boolean manage = Set.of("FARM_OWNER", "FARM_MANAGER").contains(membership.role());
    return new Access(manage, manage, false, null);
  }

  private Access accessForWrite(Long user, Long farm) {
    Access access = access(user, farm);
    // care assignment을 잠그기 전에 공통 부모 farm부터 잠근다.
    mutationGuard.lockActiveFarm(farm);
    if (access.care) {
      return new Access(false, true, true, careGuard.requireActiveForUpdate(user, farm));
    }
    FarmMembership current = farmGuard.requireFarmRoleForUpdate(
        user, farm, "FARM_OWNER", "FARM_MANAGER", "WORKER", "VIEWER");
    boolean manage = Set.of("FARM_OWNER", "FARM_MANAGER").contains(current.role());
    access = new Access(manage, manage, false, null);
    return access;
  }

  private void validateResource(Long farm, Long zoneId, String domain, Long recordId) {
    if (zoneId != null && recordMapper.findCurrentZoneForUpdate(farm, zoneId).isEmpty())
      throw new BusinessException(ErrorCode.FARM_ZONE_NOT_FOUND);
    if ((domain == null) != (recordId == null))
      throw validation("relatedDomain과 relatedRecordId는 함께 입력해야 합니다.");
    if (domain != null) {
      RecordType type = RecordType.fromPath(domain);
      if (recordMapper.findById(type, farm, recordId).isEmpty())
        throw new BusinessException(recordError(type));
    }
  }

  private ErrorCode recordError(RecordType type) {
    return switch (type) {
      case WORK -> ErrorCode.WORK_LOG_NOT_FOUND;
      case PEST_CONTROL -> ErrorCode.PEST_CONTROL_LOG_NOT_FOUND;
      case HARVEST -> ErrorCode.HARVEST_LOG_NOT_FOUND;
      case SALES -> ErrorCode.SALES_LOG_NOT_FOUND;
    };
  }

  private void validateFilters(String t, String s, String sev, String c) {
    if ((t != null && !TYPES.contains(t))
        || (s != null && !STATUSES.contains(s))
        || (sev != null && !SEVERITIES.contains(sev))
        || (c != null && !CONFIRM.contains(c))) throw validation("이슈 필터가 올바르지 않습니다.");
  }

  private Sort sort(String raw) {
    if (raw == null || raw.isBlank()) return new Sort("q.issue_date", "DESC");
    String[] p = raw.split(",", -1);
    if (p.length != 2) throw validation("sort 형식이 올바르지 않습니다.");
    String col =
        switch (p[0]) {
          case "issueDate" -> "q.issue_date";
          case "createdAt" -> "q.created_at";
          case "severity" -> "FIELD(q.severity,'HIGH','NORMAL','LOW')";
          default -> throw validation("지원하지 않는 정렬입니다.");
        };
    String dir = p[1].toUpperCase(Locale.ROOT);
    if (!Set.of("ASC", "DESC").contains(dir)) throw validation("정렬 방향이 올바르지 않습니다.");
    return new Sort(col, dir);
  }

  private void page(int p, int s) {
    if (p < 0 || s < 1 || s > 100 || (long) p * s > Integer.MAX_VALUE)
      throw validation("page는 0 이상, size는 1~100입니다.");
  }

  private void uuid(String v) {
    try {
      UUID.fromString(v);
    } catch (Exception e) {
      throw validation("clientRequestId는 UUID 형식이어야 합니다.");
    }
  }

  private String trim(String v, int max) {
    if (v == null) return null;
    String x = v.trim();
    if (x.length() > max) throw validation("입력값이 너무 깁니다.");
    return x.isBlank() ? null : x;
  }

  private void audit(IssueRow r, Long actor, String action, String detail) {
    mapper.insertAudit(
        r.getOrganizationId(),
        r.getFarmId(),
        actor,
        action,
        "DATA_QUALITY_ISSUE",
        r.getId(),
        detail,
        LocalDateTime.now());
  }

  private BusinessException validation(String m) {
    return new BusinessException(ErrorCode.VALIDATION_FAILED, m);
  }

  private BusinessException conflict() {
    return new BusinessException(ErrorCode.CONFLICT, "다른 요청이 먼저 변경했습니다.");
  }

  private record Access(
      boolean canEdit, boolean canResolve, boolean care, CareAssignmentRow assignment) {}

  private record Sort(String column, String direction) {}
}
