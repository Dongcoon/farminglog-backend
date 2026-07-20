package com.farmlog.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Phase6MapperContractTest {
  @Test
  void currentReadsTenantScopesAndOptimisticLocksArePresent() throws Exception {
    String attachment = resource("/mapper/attachment/AttachmentMapper.xml");
    String access = resource("/mapper/farmaccess/FarmAccessMapper.xml");
    String quality = resource("/mapper/dataquality/DataQualityMapper.xml");
    assertThat(attachment)
        .contains(
            "a.farm_id=#{farmId}",
            "FOR UPDATE",
            "version=version+1",
            "status='COMMITTED'",
            "expires_at&gt;#{now}",
            "care_assignment_id");
    assertThat(access)
        .contains(
            "findInvitationByTokenForUpdate",
            "invite_token_hash=#{tokenHash}",
            "FOR UPDATE",
            "findMemberByUserForUpdate",
            "findActiveAssignmentForUpdate",
            "lockAssignmentScope",
            "countOverlappingAssignments",
            "version=version+1");
    assertThat(quality)
        .contains(
            "q.farm_id=#{farmId}",
            "q.id ${direction}",
            "assigned_manager_user_id",
            "version=version+1");
  }

  @Test
  void schemaAndMigrationKeepExportCompatibilityAndBackfillRequiredKeys() throws Exception {
    String schema = Files.readString(Path.of("database/schema.sql"), StandardCharsets.UTF_8),
        changes = Files.readString(Path.of("database/db-change-log.md"), StandardCharsets.UTF_8);
    assertThat(schema)
        .contains(
            "client_file_id VARCHAR(36) NULL",
            "care_assignment_id BIGINT NULL",
            "uk_photo_batch_request",
            "ix_care_assignment_active_period");
    assertThat(changes)
        .contains(
            "UPDATE farm_invitation SET client_request_id=UUID()",
            "UPDATE farm_care_assignment SET client_request_id=UUID()",
            "UPDATE data_followup_log SET client_request_id=UUID()",
            "적용 전");
  }

  private String resource(String path) throws Exception {
    try (var stream = getClass().getResourceAsStream(path)) {
      assertThat(stream).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
