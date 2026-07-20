package com.farmlog.records;

import com.farmlog.records.dto.RecordFilter;
import com.farmlog.records.entity.RecordRow;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** MyBatis 동적 SQL과 Phase 4 DDL 핵심 불변식을 실제 DB 없이 검증한다. */
class RecordMapperContractTest {
    private static final String NS = "com.farmlog.records.mapper.RecordMapper.";

    @Test
    void mapperXmlParsesAndListQueriesAreFarmScopedWithStableSort() throws Exception {
        Configuration configuration = parse();
        RecordFilter filter = new RecordFilter(11L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20),
                4L, null, null, null, 7L, 8L, null, null, 1, 20, "l.work_date", "DESC");
        Map<String, Object> params = new HashMap<>();
        params.put("type", RecordType.WORK);
        params.put("filter", filter);

        assertThat(sql(configuration, "findPage", params))
                .contains("FROMwork_logl")
                .contains("WHEREl.farm_id=?ANDl.deleted_atISNULL")
                .contains("ANDl.work_date>=?", "ANDl.work_date<=?", "ANDl.work_type_id=?")
                .contains("ORDERBYl.work_dateDESC,l.idDESCLIMIT?OFFSET?");
        assertThat(sql(configuration, "countPage", params))
                .contains("FROMwork_loglWHEREl.farm_id=?ANDl.deleted_atISNULL");
    }

    @Test
    void detailMutationFeedAndAuthorsKeepFarmAndSoftDeleteBoundaries() throws Exception {
        Configuration configuration = parse();
        Map<String, Object> params = new HashMap<>();
        params.put("type", RecordType.SALES);
        params.put("farmId", 11L);
        params.put("id", 3L);
        assertThat(sql(configuration, "findById", params))
                .contains("FROMsales_logl")
                .contains("WHEREl.farm_id=?ANDl.id=?ANDl.deleted_atISNULL");

        params.put("expectedVersion", 2L);
        params.put("userId", 7L);
        params.put("now", null);
        assertThat(sql(configuration, "softDelete", params))
                .contains("UPDATEsales_logSETdeleted_at=?")
                .contains("WHEREfarm_id=?ANDid=?ANDversion=?ANDdeleted_atISNULL");

        Map<String, Object> feed = new HashMap<>();
        feed.put("farmId", 11L);
        feed.put("dateFrom", null);
        feed.put("dateTo", null);
        feed.put("zoneId", null);
        feed.put("createdBy", null);
        feed.put("types", List.of(RecordType.WORK, RecordType.SALES));
        feed.put("orderBy", "q.record_date");
        feed.put("direction", "DESC");
        feed.put("offset", 0);
        feed.put("size", 20);
        assertThat(sql(configuration, "findFeed", feed))
                .contains("UNIONALL")
                .contains("WHEREq.farm_id=?")
                .contains("ORDERBYq.record_dateDESC,q.idDESC,q.record_typeDESCLIMIT?OFFSET?");
        assertThat(sql(configuration, "findRecordAuthors", Map.of("farmId", 11L)))
                .contains("FROMwork_logWHEREfarm_id=?ANDdeleted_atISNULL")
                .contains("ORDERBYu.display_name,u.id");
    }

    @Test
    void insertsPersistIdempotencyAndOptimisticLockColumns() throws Exception {
        Configuration configuration = parse();
        RecordRow row = new RecordRow();
        row.setType(RecordType.SALES);
        row.setFarmId(11L);
        row.setOrganizationId(3L);
        row.setClientRequestId("123e4567-e89b-12d3-a456-426614174000");
        String insert = sql(configuration, "insertSales", row);
        assertThat(insert).contains("INSERTINTOsales_log")
                .contains("net_amount_overridden_yn", "care_assignment_id", "farmer_confirm_status", "client_request_id", "version")
                .contains("VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?)");

        Map<String, Object> update = new HashMap<>();
        row.setId(8L);
        update.put("row", row);
        update.put("expectedVersion", 0L);
        assertThat(sql(configuration, "updateSales", update))
                .contains("version=version+1")
                .contains("WHEREfarm_id=?ANDid=?ANDversion=?ANDdeleted_atISNULL");
    }

    @Test
    void schemaHasCommonRecordColumnsUniqueRequestKeysAndUtf8KoreanSeed() throws Exception {
        String schema = Files.readString(Path.of("database", "schema.sql"), StandardCharsets.UTF_8);
        for (String table : List.of("work_log", "pest_control_log", "harvest_log", "sales_log")) {
            assertThat(table(schema, table)).contains(
                    "created_role VARCHAR(30)", "care_assignment_id BIGINT", "farmer_confirm_status VARCHAR(30)",
                    "farmer_confirmed_by BIGINT", "farmer_confirmed_at DATETIME(6)", "version BIGINT NOT NULL DEFAULT 0",
                    "client_request_id VARCHAR(36)");
        }
        assertThat(table(schema, "sales_log"))
                .contains("item_name VARCHAR(150) NULL", "net_amount_overridden_yn CHAR(1) NOT NULL DEFAULT 'N'",
                        "UNIQUE KEY uk_sales_log_request (farm_id, client_request_id)");
        assertThat(table(schema, "farm")).doesNotContain("client_request_id", "farmer_confirm_status", "created_role");

        String seed = Files.readString(Path.of("database", "seed-data.sql"), StandardCharsets.UTF_8);
        assertThat(seed).contains("'DONE'", "딸기").doesNotContain("'SETTLED'", "�");

        String changeLog = Files.readString(Path.of("database", "db-change-log.md"), StandardCharsets.UTF_8);
        assertThat(changeLog)
                .contains("ALTER TABLE work_log", "ALTER TABLE pest_control_log", "ALTER TABLE harvest_log", "ALTER TABLE sales_log")
                .contains("ADD UNIQUE KEY uk_work_log_request", "ADD UNIQUE KEY uk_sales_log_request")
                .contains("UPDATE sales_log", "WHERE settlement_status = 'SETTLED'")
                .contains("중복 실행하지 않는다", "백업");
    }

    private Configuration parse() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/records/RecordMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String sql(Configuration configuration, String id, Object parameter) {
        BoundSql boundSql = configuration.getMappedStatement(NS + id).getBoundSql(parameter);
        return boundSql.getSql().replaceAll("\\s+", "").trim();
    }

    private String table(String schema, String name) {
        int start = schema.indexOf("CREATE TABLE IF NOT EXISTS " + name + " (");
        int end = schema.indexOf(") ENGINE=InnoDB", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return schema.substring(start, end);
    }
}
