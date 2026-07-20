package com.farmlog.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExportMapperContractTest {
    @Test
    void mapperContainsTenantFiltersCurrentReadsAndClaimGuards() throws Exception {
        String xml;
        try (var stream = getClass().getResourceAsStream("/mapper/export/ExportMapper.xml")) {
            assertThat(stream).isNotNull();
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(xml).contains("findByClientRequestIdForUpdate", "FOR UPDATE", "claim_token=#{claimToken}",
                "findExhaustedStale", "LOWER(TRIM(l.unit))", "l.farm_id=#{farmId}", "l.deleted_at IS NULL");
    }

    @Test
    void freshSchemaAndMigrationRequireClientRequestId() throws Exception {
        String schema = resourceFile("database/schema.sql");
        String changes = resourceFile("database/db-change-log.md");
        String exportTable = schema.substring(schema.indexOf("CREATE TABLE IF NOT EXISTS export_job"),
                schema.indexOf("CREATE TABLE IF NOT EXISTS data_quality_issue"));
        assertThat(exportTable).contains("client_request_id VARCHAR(36) NOT NULL");
        assertThat(changes).contains("MODIFY COLUMN client_request_id VARCHAR(36) NOT NULL");
    }

    private String resourceFile(String relative) throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(relative), StandardCharsets.UTF_8);
    }
}
