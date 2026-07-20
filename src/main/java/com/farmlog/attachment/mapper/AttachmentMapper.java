package com.farmlog.attachment.mapper;

import com.farmlog.attachment.entity.*;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AttachmentMapper {
  List<AttachmentRow> findByRef(
      @Param("farmId") Long farmId, @Param("refType") String refType, @Param("refId") Long refId);

  Optional<AttachmentRow> findById(@Param("farmId") Long farmId, @Param("id") Long id);

  Optional<AttachmentRow> findByClientFile(
      @Param("farmId") Long farmId,
      @Param("refType") String refType,
      @Param("refId") Long refId,
      @Param("clientFileId") String clientFileId);

  Optional<AttachmentRow> findByClientFileForUpdate(
      @Param("farmId") Long farmId,
      @Param("refType") String refType,
      @Param("refId") Long refId,
      @Param("clientFileId") String clientFileId);

  long countByRef(
      @Param("farmId") Long farmId, @Param("refType") String refType, @Param("refId") Long refId);

  int insertAttachment(AttachmentRow row);

  int softDelete(
      @Param("farmId") Long farmId,
      @Param("id") Long id,
      @Param("version") Long version,
      @Param("now") LocalDateTime now);

  Optional<PhotoBatchRow> findBatchByRequest(
      @Param("farmId") Long farmId,
      @Param("ownerUserId") Long owner,
      @Param("clientRequestId") String request);

  Optional<PhotoBatchRow> findBatch(
      @Param("farmId") Long farmId, @Param("ownerUserId") Long owner, @Param("id") String id);

  Optional<PhotoBatchRow> lockBatch(
      @Param("farmId") Long farmId, @Param("ownerUserId") Long owner, @Param("id") String id);

  int insertBatch(PhotoBatchRow row);

  List<PhotoBatchFileRow> findBatchFiles(@Param("batchId") String batchId);

  Optional<PhotoBatchFileRow> findBatchFile(
      @Param("batchId") String batchId, @Param("clientFileId") String clientFileId);

  Optional<PhotoBatchFileRow> findBatchFileForUpdate(
      @Param("batchId") String batchId, @Param("clientFileId") String clientFileId);

  int insertBatchFile(PhotoBatchFileRow row);

  int markCommitted(
      @Param("id") String id,
      @Param("commitRequestId") String commitRequestId,
      @Param("issueId") Long issueId,
      @Param("now") LocalDateTime now);

  List<PhotoBatchRow> findExpiredBatches(
      @Param("now") LocalDateTime now, @Param("limit") int limit);

  int markBatchExpired(@Param("id") String id, @Param("now") LocalDateTime now);

  List<String> findRetainedAttachmentPaths();

  List<String> findRetainedStagingPaths();

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
