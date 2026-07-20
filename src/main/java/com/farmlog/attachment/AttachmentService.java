package com.farmlog.attachment;

import com.farmlog.attachment.dto.*;
import com.farmlog.attachment.entity.*;
import com.farmlog.attachment.mapper.AttachmentMapper;
import com.farmlog.common.exception.*;
import com.farmlog.common.tenant.*;
import com.farmlog.dataquality.DataQualityService;
import com.farmlog.dataquality.entity.IssueRow;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmaccess.*;
import com.farmlog.farmaccess.entity.CareAssignmentRow;
import com.farmlog.records.*;
import com.farmlog.records.entity.RecordRow;
import com.farmlog.records.mapper.RecordMapper;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
  private static final Set<String> WRITERS =
      Set.of("FARM_OWNER", "FARM_MANAGER", "WORKER", "FARM_CARE_MANAGER");
  private static final Set<String> ESTIMATED =
      Set.of("WORK", "PEST_CONTROL", "HARVEST", "SALES", "UNKNOWN");
  private final AttachmentMapper mapper;
  private final RecordMapper recordMapper;
  private final FarmAccessGuard farmGuard;
  private final CareAccessGuard careGuard;
  private final FarmMapper farmMapper;
  private final ImageValidator validator;
  private final AttachmentStorage storage;
  private final DataQualityService dataQuality;
  private final FarmMutationGuard mutationGuard;

  @Autowired
  public AttachmentService(
      AttachmentMapper mapper,
      RecordMapper recordMapper,
      FarmAccessGuard farmGuard,
      CareAccessGuard careGuard,
      FarmMapper farmMapper,
      ImageValidator validator,
      AttachmentStorage storage,
      DataQualityService dataQuality,
      FarmMutationGuard mutationGuard) {
    this.mapper = mapper;
    this.recordMapper = recordMapper;
    this.farmGuard = farmGuard;
    this.careGuard = careGuard;
    this.farmMapper = farmMapper;
    this.validator = validator;
    this.storage = storage;
    this.dataQuality = dataQuality;
    this.mutationGuard = mutationGuard;
  }

  public AttachmentService(
      AttachmentMapper mapper,
      RecordMapper recordMapper,
      FarmAccessGuard farmGuard,
      CareAccessGuard careGuard,
      FarmMapper farmMapper,
      ImageValidator validator,
      AttachmentStorage storage,
      DataQualityService dataQuality) {
    this(mapper, recordMapper, farmGuard, careGuard, farmMapper, validator, storage, dataQuality,
        new FarmMutationGuard(farmMapper));
  }

  public List<AttachmentDto> list(Long user, Long farm, String domain, Long recordId) {
    Access a = access(user, farm);
    RecordRef ref = record(domain, farm, recordId);
    return mapper.findByRef(farm, ref.refType, recordId).stream()
        .map(r -> dto(r, a, ref.row))
        .toList();
  }

  public AttachmentDownload download(Long user, Long farm, Long id) {
    access(user, farm);
    AttachmentRow row =
        mapper
            .findById(farm, id)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
    authorizeReference(user, farm, row);
    return new AttachmentDownload(
        storage.existing(row.getStoragePath()),
        row.getOriginalFileName(),
        row.getContentType(),
        row.getFileSize());
  }

  @Transactional
  public AttachmentDto upload(
      Long user, Long farm, String domain, Long recordId, String clientFileId, MultipartFile file) {
    Access a = accessForWrite(user, farm);
    if (!WRITERS.contains(a.role)) throw new BusinessException(ErrorCode.FORBIDDEN);
    uuid(clientFileId);
    RecordRef ref = record(domain, farm, recordId);
    if (a.care
        && (!Objects.equals(ref.row.getCareAssignmentId(), a.assignment.getId())
            || !Objects.equals(ref.row.getCreatedBy(), user)))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    var existing = mapper.findByClientFile(farm, ref.refType, recordId, clientFileId);
    if (existing.isPresent()) return dto(existing.get(), a, ref.row);
    recordMapper
        .lockRecord(ref.type, farm, recordId)
        .orElseThrow(() -> new BusinessException(recordError(ref.type)));
    if (mapper.countByRef(farm, ref.refType, recordId) >= 5)
      throw validation("기록에는 사진을 최대 5장 첨부할 수 있습니다.");
    ImageInspection inspected = validator.inspect(file);
    AttachmentStorage.Stored stored;
    try {
      stored = storage.writeFinal(farm, inspected.extension(), inspected.bytes());
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "사진을 저장할 수 없습니다.");
    }
    rollbackDelete(stored.relativePath());
    AttachmentRow row =
        attachmentBase(user, farm, ref.refType, recordId, clientFileId, file, inspected, stored);
    try {
      mapper.insertAttachment(row);
    } catch (DuplicateKeyException ex) {
      storage.deleteQuietly(stored.relativePath());
      return dto(
          mapper
              .findByClientFileForUpdate(farm, ref.refType, recordId, clientFileId)
              .orElseThrow(() -> ex),
          a,
          ref.row);
    }
    audit(row, user, "ATTACHMENT_CREATED", "{\"refType\":\"" + ref.refType + "\"}");
    return dto(row, a, ref.row);
  }

  @Transactional
  public void delete(Long user, Long farm, Long id, Long version) {
    Access a = accessForWrite(user, farm);
    AttachmentRow row =
        mapper
            .findById(farm, id)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
    RecordRow record = authorizeReference(user, farm, row);
    if ("DATA_QUALITY_ISSUE".equals(row.getRefType())
        && !Set.of("FARM_OWNER", "FARM_MANAGER").contains(a.role))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    if (!canDelete(row, a, record)) throw new BusinessException(ErrorCode.FORBIDDEN);
    if (mapper.softDelete(farm, id, version, LocalDateTime.now()) != 1) throw conflict();
    afterCommit(() -> storage.deleteQuietly(row.getStoragePath()));
    audit(row, user, "ATTACHMENT_DELETED", "{\"version\":" + version + "}");
  }

  @Transactional
  public PhotoDraftDtos.Response createDraft(
      Long user, Long farm, PhotoDraftDtos.CreateRequest req) {
    Access a = accessForWrite(user, farm);
    if (!WRITERS.contains(a.role)) throw new BusinessException(ErrorCode.FORBIDDEN);
    uuid(req.clientRequestId());
    if (!ESTIMATED.contains(req.estimatedRecordType())) throw validation("추정 기록 유형이 올바르지 않습니다.");
    if (req.issueDate().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))))
      throw validation("미래 날짜는 사용할 수 없습니다.");
    if (req.zoneId() != null && recordMapper.findZone(farm, req.zoneId(), true).isEmpty())
      throw new BusinessException(ErrorCode.FARM_ZONE_NOT_FOUND);
    var old = mapper.findBatchByRequest(farm, user, req.clientRequestId());
    if (old.isPresent()) {
      ensureBatchAccess(a, old.get());
      return batchDto(old.get());
    }
    FarmEntity f =
        farmMapper
            .findById(farm)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    PhotoBatchRow row = new PhotoBatchRow();
    row.setId(UUID.randomUUID().toString());
    row.setOrganizationId(f.getOrganizationId());
    row.setFarmId(farm);
    row.setOwnerUserId(user);
    row.setCareAssignmentId(a.care ? a.assignment.getId() : null);
    row.setClientRequestId(req.clientRequestId());
    row.setIssueDate(req.issueDate());
    row.setZoneId(req.zoneId());
    row.setEstimatedRecordType(req.estimatedRecordType());
    row.setMemo(trim(req.memo(), 4000));
    row.setFileCount(req.fileCount());
    row.setStatus("UPLOADING");
    row.setCreatedAt(LocalDateTime.now());
    row.setExpiresAt(row.getCreatedAt().plusHours(24));
    try {
      mapper.insertBatch(row);
    } catch (DuplicateKeyException e) {
      PhotoBatchRow duplicate =
          mapper.findBatchByRequest(farm, user, req.clientRequestId()).orElseThrow(() -> e);
      ensureBatchAccess(a, duplicate);
      return batchDto(duplicate);
    }
    return batchDto(row);
  }

  @Transactional
  public PhotoDraftDtos.Response uploadDraftFile(
      Long user, Long farm, String batchId, String clientFileId, MultipartFile file) {
    Access a = accessForWrite(user, farm);
    uuid(batchId);
    uuid(clientFileId);
    PhotoBatchRow batch =
        mapper
            .lockBatch(farm, user, batchId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
    ensureBatchAccess(a, batch);
    ensureUploadable(batch);
    var old = mapper.findBatchFile(batchId, clientFileId);
    if (old.isPresent()) return batchDto(batch);
    if (mapper.findBatchFiles(batchId).size() >= batch.getFileCount())
      throw validation("예정된 사진 수를 초과했습니다.");
    ImageInspection inspected = validator.inspect(file);
    AttachmentStorage.Stored stored;
    try {
      stored = storage.writeStaging(user, farm, batchId, inspected.extension(), inspected.bytes());
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "사진을 임시 저장할 수 없습니다.");
    }
    rollbackDelete(stored.relativePath());
    PhotoBatchFileRow row = new PhotoBatchFileRow();
    row.setBatchId(batchId);
    row.setClientFileId(clientFileId);
    row.setOriginalFileName(safeName(file.getOriginalFilename()));
    row.setStoredFileName(stored.storedName());
    row.setContentType(inspected.contentType());
    row.setFileSize((long) inspected.bytes().length);
    row.setStagingPath(stored.relativePath());
    row.setStatus("UPLOADED");
    row.setUploadedAt(LocalDateTime.now());
    try {
      mapper.insertBatchFile(row);
    } catch (DuplicateKeyException e) {
      storage.deleteQuietly(stored.relativePath());
      mapper.findBatchFileForUpdate(batchId, clientFileId).orElseThrow(() -> e);
    }
    return batchDto(batch);
  }

  @Transactional
  public PhotoDraftDtos.CommitResponse commit(
      Long user, Long farm, String batchId, PhotoDraftDtos.CommitRequest req) {
    Access access = accessForWrite(user, farm);
    uuid(batchId);
    uuid(req.clientRequestId());
    PhotoBatchRow batch =
        mapper
            .lockBatch(farm, user, batchId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
    ensureBatchAccess(access, batch);
    if ("COMMITTED".equals(batch.getStatus())) {
      if (!Objects.equals(batch.getCommitRequestId(), req.clientRequestId())) throw conflict();
      return commitDto(user, farm, batch, access);
    }
    ensureUploadable(batch);
    List<PhotoBatchFileRow> files = mapper.findBatchFiles(batchId);
    if (files.size() != batch.getFileCount())
      throw new BusinessException(ErrorCode.ATTACHMENT_NOT_READY);
    IssueRow issue =
        dataQuality.createPhotoOnly(
            user,
            farm,
            batch.getZoneId(),
            batch.getIssueDate(),
            batch.getEstimatedRecordType(),
            batch.getMemo());
    for (PhotoBatchFileRow file : files) {
      String path;
      try {
        path = storage.promote(file.getStagingPath(), farm);
      } catch (IOException e) {
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "사진을 최종 저장할 수 없습니다.");
      }
      // promote 직후 보상을 등록해 이후 DB 예외에서도 final 파일이 남지 않게 한다.
      rollbackDelete(path);
      AttachmentRow row = new AttachmentRow();
      row.setOrganizationId(batch.getOrganizationId());
      row.setFarmId(farm);
      row.setRefType("DATA_QUALITY_ISSUE");
      row.setRefId(issue.getId());
      row.setClientFileId(file.getClientFileId());
      row.setOriginalFileName(file.getOriginalFileName());
      row.setStoredFileName(PathName.file(path));
      row.setContentType(file.getContentType());
      row.setFileSize(file.getFileSize());
      row.setStoragePath(path);
      row.setCreatedBy(user);
      row.setCreatedAt(LocalDateTime.now());
      mapper.insertAttachment(row);
    }
    if (mapper.markCommitted(batchId, req.clientRequestId(), issue.getId(), LocalDateTime.now())
        != 1) throw conflict();
    files.forEach(f -> afterCommit(() -> storage.deleteQuietly(f.getStagingPath())));
    auditIssue(batch, user, issue.getId());
    PhotoBatchRow committed = mapper.findBatch(farm, user, batchId).orElseThrow();
    return commitDto(user, farm, committed, access);
  }

  @Scheduled(
      fixedDelayString = "${farmlog.file.staging-cleanup-ms:3600000}",
      initialDelayString = "${farmlog.file.staging-cleanup-initial-ms:60000}")
  @Transactional
  public void cleanupExpired() {
    expireAndScheduleCleanup();
  }

  @Transactional
  @EventListener(ApplicationReadyEvent.class)
  public void cleanupOnStartup() {
    expireAndScheduleCleanup();
  }

  private void expireAndScheduleCleanup() {
    LocalDateTime now = LocalDateTime.now();
    for (PhotoBatchRow b : mapper.findExpiredBatches(now, 100)) {
      if (mapper.markBatchExpired(b.getId(), now) == 1)
        mapper
            .findBatchFiles(b.getId())
            .forEach(f -> afterCommit(() -> storage.deleteQuietly(f.getStagingPath())));
    }
    afterCommit(this::cleanupOrphans);
  }

  private void cleanupOrphans() {
    Set<String> retained = new HashSet<>(mapper.findRetainedAttachmentPaths());
    retained.addAll(mapper.findRetainedStagingPaths());
    storage.cleanupOrphans(retained);
  }

  private PhotoDraftDtos.CommitResponse commitDto(
      Long user, Long farm, PhotoBatchRow batch, Access a) {
    if (batch.getIssueId() == null) throw conflict();
    return new PhotoDraftDtos.CommitResponse(
        dataQuality.detail(user, farm, batch.getIssueId()),
        mapper.findByRef(farm, "DATA_QUALITY_ISSUE", batch.getIssueId()).stream()
            .map(r -> dto(r, a, null))
            .toList());
  }

  private PhotoDraftDtos.Response batchDto(PhotoBatchRow b) {
    List<String> ids =
        mapper.findBatchFiles(b.getId()).stream().map(PhotoBatchFileRow::getClientFileId).toList();
    return new PhotoDraftDtos.Response(b.getId(), b.getStatus(), b.getExpiresAt(), ids);
  }

  private void ensureBatchAccess(Access access, PhotoBatchRow batch) {
    if (batch.getCareAssignmentId() == null) {
      if (access.care) throw new BusinessException(ErrorCode.FORBIDDEN);
      return;
    }
    if (!access.care || !Objects.equals(batch.getCareAssignmentId(), access.assignment.getId()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
  }

  private void ensureUploadable(PhotoBatchRow b) {
    if (!"UPLOADING".equals(b.getStatus())) throw new BusinessException(ErrorCode.CONFLICT);
    if (!b.getExpiresAt().isAfter(LocalDateTime.now()))
      throw new BusinessException(ErrorCode.PHOTO_DRAFT_EXPIRED);
  }

  private AttachmentRow attachmentBase(
      Long user,
      Long farm,
      String refType,
      Long refId,
      String client,
      MultipartFile file,
      ImageInspection i,
      AttachmentStorage.Stored s) {
    FarmEntity f =
        farmMapper
            .findById(farm)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    AttachmentRow r = new AttachmentRow();
    r.setOrganizationId(f.getOrganizationId());
    r.setFarmId(farm);
    r.setRefType(refType);
    r.setRefId(refId);
    r.setClientFileId(client);
    r.setOriginalFileName(safeName(file.getOriginalFilename()));
    r.setStoredFileName(s.storedName());
    r.setContentType(i.contentType());
    r.setFileSize((long) i.bytes().length);
    r.setStoragePath(s.relativePath());
    r.setCreatedBy(user);
    r.setCreatedAt(LocalDateTime.now());
    r.setVersion(0L);
    return r;
  }

  private AttachmentDto dto(AttachmentRow r, Access a, RecordRow record) {
    return new AttachmentDto(
        r.getId(),
        r.getVersion(),
        r.getFarmId(),
        r.getClientFileId(),
        r.getOriginalFileName(),
        r.getContentType(),
        r.getFileSize(),
        new AttachmentDto.Actor(r.getCreatedBy(), r.getCreatedByName()),
        r.getCreatedAt(),
        canDelete(r, a, record));
  }

  private boolean canDelete(AttachmentRow r, Access a, RecordRow record) {
    if (Set.of("FARM_OWNER", "FARM_MANAGER").contains(a.role)) return true;
    if ("DATA_QUALITY_ISSUE".equals(r.getRefType())) return false;
    if (!Objects.equals(r.getCreatedBy(), a.user)) return false;
    return !a.care
        || (record != null
            && Objects.equals(record.getCareAssignmentId(), a.assignment.getId())
            && Objects.equals(record.getCreatedBy(), a.user));
  }

  /** generic endpoint는 이미지 리소스만 허용하며 export 파일은 전용 API로만 내려준다. */
  private RecordRow authorizeReference(Long user, Long farm, AttachmentRow attachment) {
    return switch (attachment.getRefType()) {
      case "WORK", "PEST_CONTROL", "HARVEST", "SALES" ->
          record(type(attachment.getRefType()).path(), farm, attachment.getRefId()).row;
      case "DATA_QUALITY_ISSUE" -> {
        dataQuality.detail(user, farm, attachment.getRefId());
        yield null;
      }
      default -> throw new BusinessException(ErrorCode.FORBIDDEN);
    };
  }

  private RecordRef record(String domain, Long farm, Long id) {
    RecordType type = RecordType.fromPath(domain);
    RecordRow row =
        recordMapper
            .findById(type, farm, id)
            .orElseThrow(() -> new BusinessException(recordError(type)));
    return new RecordRef(type, type.name(), row);
  }

  private ErrorCode recordError(RecordType t) {
    return switch (t) {
      case WORK -> ErrorCode.WORK_LOG_NOT_FOUND;
      case PEST_CONTROL -> ErrorCode.PEST_CONTROL_LOG_NOT_FOUND;
      case HARVEST -> ErrorCode.HARVEST_LOG_NOT_FOUND;
      case SALES -> ErrorCode.SALES_LOG_NOT_FOUND;
    };
  }

  private RecordType type(String ref) {
    return RecordType.valueOf(ref);
  }

  private Access access(Long user, Long farm) {
    FarmMembership membership;
    try {
      membership = farmGuard.requireFarmMember(user, farm);
    } catch (BusinessException e) {
      if (e.getErrorCode() != ErrorCode.FORBIDDEN) throw e;
      CareAssignmentRow c = careGuard.requireActive(user, farm);
      return new Access(user, "FARM_CARE_MANAGER", true, c);
    }
    if ("FARM_CARE_MANAGER".equals(membership.role()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    return new Access(user, membership.role(), false, null);
  }

  private Access accessForWrite(Long user, Long farm) {
    Access access = access(user, farm);
    // 파일/배치/care assignment보다 부모 농장을 먼저 잠가 공통 순서를 유지한다.
    mutationGuard.lockActiveFarm(farm);
    if (access.care) {
      return new Access(user, access.role, true, careGuard.requireActiveForUpdate(user, farm));
    }
    FarmMembership current = farmGuard.requireFarmRoleForUpdate(
        user, farm, "FARM_OWNER", "FARM_MANAGER", "WORKER", "VIEWER");
    return new Access(user, current.role(), false, null);
  }

  private void audit(AttachmentRow r, Long user, String action, String detail) {
    mapper.insertAudit(
        r.getOrganizationId(),
        r.getFarmId(),
        user,
        action,
        "ATTACHMENT",
        r.getId(),
        detail,
        LocalDateTime.now());
  }

  private void auditIssue(PhotoBatchRow b, Long user, Long issue) {
    mapper.insertAudit(
        b.getOrganizationId(),
        b.getFarmId(),
        user,
        "PHOTO_ONLY_COMMITTED",
        "DATA_QUALITY_ISSUE",
        issue,
        "{\"fileCount\":" + b.getFileCount() + "}",
        LocalDateTime.now());
  }

  private void rollbackDelete(String path) {
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED) storage.deleteQuietly(path);
            }
          });
  }

  private void afterCommit(Runnable r) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      r.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            r.run();
          }
        });
  }

  private void uuid(String v) {
    try {
      UUID.fromString(v);
    } catch (Exception e) {
      throw validation("UUID 형식이 올바르지 않습니다.");
    }
  }

  private String safeName(String n) {
    String source = Optional.ofNullable(n).orElse("image"),
        v =
            source
                .codePoints()
                .map(c -> c == '/' || c == '\\' || Character.isISOControl(c) ? '_' : c)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    int count = v.codePointCount(0, v.length());
    return count > 255 ? v.substring(v.offsetByCodePoints(0, count - 255)) : v;
  }

  private String trim(String v, int max) {
    if (v == null) return null;
    String x = v.trim();
    if (x.length() > max) throw validation("입력값이 너무 깁니다.");
    return x.isBlank() ? null : x;
  }

  private BusinessException validation(String m) {
    return new BusinessException(ErrorCode.VALIDATION_FAILED, m);
  }

  private BusinessException conflict() {
    return new BusinessException(ErrorCode.CONFLICT, "다른 요청이 먼저 변경했습니다.");
  }

  private record Access(Long user, String role, boolean care, CareAssignmentRow assignment) {}

  private record RecordRef(RecordType type, String refType, RecordRow row) {}

  private static final class PathName {
    static String file(String p) {
      int i = p.lastIndexOf('/');
      return i < 0 ? p : p.substring(i + 1);
    }
  }
}
