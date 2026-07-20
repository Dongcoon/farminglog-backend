package com.farmlog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase7MapperContractTest {
  @Test
  void systemAdminCapabilityRequiresActiveSystemOrganizationAndMembership() throws Exception {
    String access = resource("/mapper/user/UserAccessMapper.xml");

    assertThat(access)
        .contains(
            "om.role='SYSTEM_ADMIN'",
            "om.status='ACTIVE'",
            "o.org_type='SYSTEM'",
            "o.status='ACTIVE'",
            "o.deleted_at IS NULL");
  }

  @Test
  void adminQueriesHaveExactFiltersStableTieBreakersAndSingleAuditTarget() throws Exception {
    String admin = resource("/mapper/admin/AdminMapper.xml");

    assertThat(admin)
        .contains(
            "organizationId!=null",
            "ownerUserId!=null",
            "lifecycleStatus!=null",
            "actorUserId!=null",
            "createdFrom!=null",
            "createdToExclusive!=null",
            "u.id ${direction}",
            "f.id ${direction}",
            "a.id ${direction}",
            "target_type,target_id",
            "'ADMIN_QUERY'")
        .doesNotContain("${q}");
  }

  @Test
  void schemaAndExistingDatabaseMigrationContainAuditCreatedIdIndex() throws Exception {
    String schema = Files.readString(Path.of("database/schema.sql"), StandardCharsets.UTF_8);
    String changes = Files.readString(Path.of("database/db-change-log.md"), StandardCharsets.UTF_8);

    assertThat(schema).contains("KEY ix_audit_created_id (created_at, id)");
    assertThat(changes)
        .contains("ALTER TABLE audit_log ADD KEY ix_audit_created_id (created_at, id)");
  }

  private String resource(String path) throws Exception {
    try (var stream = getClass().getResourceAsStream(path)) {
      assertThat(stream).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
