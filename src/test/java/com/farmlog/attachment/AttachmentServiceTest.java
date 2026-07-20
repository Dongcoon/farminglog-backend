package com.farmlog.attachment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.farmlog.attachment.dto.PhotoDraftDtos;
import com.farmlog.attachment.entity.*;
import com.farmlog.attachment.mapper.AttachmentMapper;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.*;
import com.farmlog.dataquality.DataQualityService;
import com.farmlog.dataquality.entity.IssueRow;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmaccess.CareAccessGuard;
import com.farmlog.farmaccess.entity.CareAssignmentRow;
import com.farmlog.records.RecordType;
import com.farmlog.records.entity.RecordRow;
import com.farmlog.records.mapper.RecordMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;

class AttachmentServiceTest {
  private AttachmentMapper mapper;
  private RecordMapper records;
  private FarmAccessGuard farmGuard;
  private CareAccessGuard careGuard;
  private AttachmentStorage storage;
  private DataQualityService quality;
  private AttachmentService service;

  @BeforeEach
  void setUp() {
    mapper = mock(AttachmentMapper.class);
    records = mock(RecordMapper.class);
    farmGuard = mock(FarmAccessGuard.class);
    careGuard = mock(CareAccessGuard.class);
    storage = mock(AttachmentStorage.class);
    quality = mock(DataQualityService.class);
    service =
        new AttachmentService(
            mapper,
            records,
            farmGuard,
            careGuard,
            mock(FarmMapper.class),
            mock(ImageValidator.class),
            storage,
            quality,
            mock(FarmMutationGuard.class));
    when(farmGuard.requireFarmRoleForUpdate(anyLong(), anyLong(), any(String[].class)))
        .thenAnswer(invocation -> new FarmMembership(
            invocation.getArgument(1), invocation.getArgument(0), "FARM_OWNER"));
  }

  @Test
  void genericDownloadRejectsExportFiles() {
    when(farmGuard.requireFarmMember(7L, 11L))
        .thenReturn(new FarmMembership(11L, 7L, "FARM_OWNER"));
    AttachmentRow row = new AttachmentRow();
    row.setRefType("EXPORT_JOB");
    row.setRefId(3L);
    when(mapper.findById(11L, 5L)).thenReturn(Optional.of(row));
    assertThatThrownBy(() -> service.download(7L, 11L, 5L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    verify(storage, never()).existing(anyString());
  }

  @Test
  void committedBatchRequiresSameCommitRequestId() {
    when(farmGuard.requireFarmMember(7L, 11L))
        .thenReturn(new FarmMembership(11L, 7L, "FARM_OWNER"));
    PhotoBatchRow batch = batch();
    batch.setStatus("COMMITTED");
    batch.setCommitRequestId("123e4567-e89b-12d3-a456-426614174000");
    when(mapper.lockBatch(11L, 7L, batch.getId())).thenReturn(Optional.of(batch));
    assertThatThrownBy(
            () ->
                service.commit(
                    7L,
                    11L,
                    batch.getId(),
                    new PhotoDraftDtos.CommitRequest("123e4567-e89b-12d3-a456-426614174001")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
  }

  @Test
  void promotedFileIsCompensatedWhenDatabaseInsertFails() throws Exception {
    when(farmGuard.requireFarmMember(7L, 11L))
        .thenReturn(new FarmMembership(11L, 7L, "FARM_OWNER"));
    PhotoBatchRow batch = batch();
    when(mapper.lockBatch(11L, 7L, batch.getId())).thenReturn(Optional.of(batch));
    PhotoBatchFileRow file = new PhotoBatchFileRow();
    file.setClientFileId("123e4567-e89b-12d3-a456-426614174002");
    file.setStagingPath("staging/a.jpg");
    file.setOriginalFileName("a.jpg");
    file.setContentType("image/jpeg");
    file.setFileSize(10L);
    when(mapper.findBatchFiles(batch.getId())).thenReturn(List.of(file));
    IssueRow issue = new IssueRow();
    issue.setId(8L);
    when(quality.createPhotoOnly(anyLong(), anyLong(), any(), any(), anyString(), any()))
        .thenReturn(issue);
    when(storage.promote("staging/a.jpg", 11L)).thenReturn("attachments/11/final.jpg");
    doThrow(new RuntimeException("db down")).when(mapper).insertAttachment(any());
    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThatThrownBy(
              () ->
                  service.commit(
                      7L,
                      11L,
                      batch.getId(),
                      new PhotoDraftDtos.CommitRequest("123e4567-e89b-12d3-a456-426614174003")))
          .isInstanceOf(RuntimeException.class);
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
      verify(storage).deleteQuietly("attachments/11/final.jpg");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void careBatchIsBoundToCurrentAssignment() {
    when(farmGuard.requireFarmMember(7L, 11L))
        .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
    CareAssignmentRow current = new CareAssignmentRow();
    current.setId(99L);
    when(careGuard.requireActiveForUpdate(7L, 11L)).thenReturn(current);
    PhotoBatchRow batch = batch();
    batch.setCareAssignmentId(88L);
    when(mapper.lockBatch(11L, 7L, batch.getId())).thenReturn(Optional.of(batch));
    assertThatThrownBy(
            () ->
                service.commit(
                    7L,
                    11L,
                    batch.getId(),
                    new PhotoDraftDtos.CommitRequest("123e4567-e89b-12d3-a456-426614174003")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    verify(careGuard).requireActiveForUpdate(7L, 11L);
  }

  @Test
  void careRecordUploadLocksCurrentAssignmentBeforeMutation() {
    when(farmGuard.requireFarmMember(7L, 11L))
        .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
    CareAssignmentRow current = new CareAssignmentRow();
    current.setId(99L);
    when(careGuard.requireActiveForUpdate(7L, 11L)).thenReturn(current);
    RecordRow record = new RecordRow();
    record.setId(4L);
    record.setFarmId(11L);
    record.setCreatedBy(7L);
    record.setCareAssignmentId(99L);
    when(records.findById(RecordType.WORK, 11L, 4L)).thenReturn(Optional.of(record));

    assertThatThrownBy(
            () ->
                service.upload(
                    7L, 11L, "work-logs", 4L, "123e4567-e89b-12d3-a456-426614174020", null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WORK_LOG_NOT_FOUND));
    verify(careGuard).requireActiveForUpdate(7L, 11L);
  }

  private PhotoBatchRow batch() {
    PhotoBatchRow b = new PhotoBatchRow();
    b.setId("123e4567-e89b-12d3-a456-426614174010");
    b.setFarmId(11L);
    b.setOwnerUserId(7L);
    b.setOrganizationId(3L);
    b.setStatus("UPLOADING");
    b.setFileCount(1);
    b.setIssueDate(LocalDate.now());
    b.setEstimatedRecordType("UNKNOWN");
    b.setExpiresAt(LocalDateTime.now().plusHours(1));
    return b;
  }
}
