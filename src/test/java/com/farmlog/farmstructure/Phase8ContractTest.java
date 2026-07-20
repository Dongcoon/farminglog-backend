package com.farmlog.farmstructure;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class Phase8ContractTest {
  @Test void schemaContainsVersionCurrentMarkerAndCanonicalEventColumns() throws Exception {
    String schema=Files.readString(Path.of("database/schema.sql"),StandardCharsets.UTF_8);
    assertThat(schema).contains("structure_version BIGINT NOT NULL DEFAULT 0")
        .contains("current_marker TINYINT AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END) PERSISTENT")
        .contains("UNIQUE KEY uk_zone_assignment_current (zone_id, current_marker)")
        .contains("request_snapshot_json JSON NOT NULL")
        .contains("period_end_exclusive DATE NULL");
  }

  @Test void mapperLocksInStableOrderAndKeepsProjectionJoinsFarmIndependent() throws Exception {
    String xml=Files.readString(Path.of("src/main/resources/mapper/farmstructure/FarmStructureMapper.xml"),StandardCharsets.UTF_8);
    assertThat(xml).contains("ORDER BY f.id FOR UPDATE").contains("ORDER BY z.id FOR UPDATE")
        .contains("a.farm_id=z.farm_id AND a.effective_to IS NULL AND a.active_yn='Y'")
        .contains("LEFT JOIN farm_zone z ON z.id=l.zone_id")
        .doesNotContain("LEFT JOIN farm_zone z ON z.farm_id=l.farm_id AND z.id=l.zone_id");
  }

  @Test void historicalZoneLabelsUseGlobalZoneIdAcrossEveryReadSurface() throws Exception {
    for (String path : java.util.List.of(
        "src/main/resources/mapper/records/RecordMapper.xml",
        "src/main/resources/mapper/report/ReportMapper.xml",
        "src/main/resources/mapper/farmaccess/FarmAccessMapper.xml",
        "src/main/resources/mapper/export/ExportMapper.xml",
        "src/main/resources/mapper/dataquality/DataQualityMapper.xml")) {
      String xml=Files.readString(Path.of(path),StandardCharsets.UTF_8);
      assertThat(xml).as(path)
          .doesNotContain("z.id=l.zone_id AND z.farm_id=l.farm_id")
          .doesNotContain("z.id=q.zone_id AND z.farm_id=q.farm_id")
          .doesNotContain("z.id=q.zone_id AND z.farm_id=q.farm_id")
          .doesNotContain("z.farm_id=l.farm_id AND z.id=l.zone_id");
    }
  }

  @Test void migrationBackfillsRequiredJsonAndDropsLegacyColumns() throws Exception {
    String migration=Files.readString(Path.of("database/db-change-log.md"),StandardCharsets.UTF_8);
    assertThat(migration).contains("invalid_structure_event_backfill")
        .contains("MODIFY COLUMN request_snapshot_json JSON NOT NULL")
        .contains("invalid_period_backfill")
        .contains("DROP COLUMN include_history_yn")
        .contains("effective_to <= effective_from");
  }

  @Test void contextContentAndCountUseSameActiveOwnerBoundary() throws Exception {
    String xml=Files.readString(Path.of("src/main/resources/mapper/farmstructure/FarmStructureMapper.xml"),StandardCharsets.UTF_8);
    assertThat(count(xml,"f.status='ACTIVE' AND f.lifecycle_status='ACTIVE'")).isGreaterThanOrEqualTo(4);
  }

  @Test void confirmCancelUseCurrentReadAndOwnerEventFilterCannotBeVacuouslyTrue() throws Exception {
    String service=Files.readString(Path.of("src/main/java/com/farmlog/farmstructure/FarmStructureService.java"),StandardCharsets.UTF_8);
    String xml=Files.readString(Path.of("src/main/resources/mapper/farmstructure/FarmStructureMapper.xml"),StandardCharsets.UTF_8);
    assertThat(count(service,"@Transactional(isolation=Isolation.READ_COMMITTED)")).isEqualTo(2);
    assertThat(service.indexOf("claimConfirmRequest")).isLessThan(service.indexOf("if(newTarget)"));
    assertThat(service).contains("recordCancelRelations(cancel.getId(), items, now)")
        .contains("SEASON_DEPENDENCY_INCLUDED")
        .contains("EXISTING_TARGET_MEMBERS_EXCLUDED");
    assertThat(xml).contains("AND EXISTS(SELECT 1 FROM farm_structure_event_item ri")
        .contains("countNewerRelatedEvents")
        .contains("claimConfirmRequest")
        .contains("e.confirmed_at=#{confirmedAt} AND e.id&gt;#{eventId}");
  }
  private int count(String value,String token){int n=0,p=0;while((p=value.indexOf(token,p))>=0){n++;p+=token.length();}return n;}
}
