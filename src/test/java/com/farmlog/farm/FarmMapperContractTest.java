package com.farmlog.farm;

import com.farmlog.farm.entity.FarmZoneEntity;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 DB 없이도 Phase 2 MyBatis XML을 파싱하고 핵심 SQL/DDL 계약을 대조한다. 서비스 Mock 테스트만으로는
 * 잡히지 않는 XML 문법, statement id, 파라미터명 및 컬럼 누락을 빌드 단계에서 조기에 발견하기 위함이다.
 */
class FarmMapperContractTest {

    @Test
    void mapperXml_parsesAndKeepsTenantSafeZoneUpdateSql() throws Exception {
        Configuration configuration = parseMappers(
                "mapper/auth/AuthMapper.xml",
                "mapper/farm/FarmMapper.xml",
                "mapper/farm/FarmZoneMapper.xml",
                "mapper/tenant/FarmMembershipMapper.xml");

        FarmZoneEntity zone = FarmZoneEntity.builder().id(10L).farmId(1L).name("1동")
                .zoneType("FIELD").displayOrder(0).build();
        String updateSql = normalizedSql(configuration,
                "com.farmlog.farm.mapper.FarmZoneMapper.update", zone);
        assertThat(updateSql)
                .contains("zone_type = ?")
                .contains("WHERE id = ? AND farm_id = ? AND deleted_at IS NULL");

        Map<String, Object> deactivateParams = new HashMap<>();
        deactivateParams.put("farmId", 1L);
        deactivateParams.put("id", 10L);
        deactivateParams.put("updatedBy", 42L);
        deactivateParams.put("updatedAt", null);
        String deactivateSql = normalizedSql(configuration,
                "com.farmlog.farm.mapper.FarmZoneMapper.deactivate", deactivateParams);
        assertThat(deactivateSql).contains("WHERE id = ? AND farm_id = ? AND deleted_at IS NULL");

        String organizationSql = normalizedSql(configuration,
                "com.farmlog.auth.mapper.AuthMapper.findActiveOrganizationMembershipByUserId",
                Map.of("userId", 42L));
        assertThat(organizationSql)
                .contains("o.org_type = 'PERSONAL'")
                .contains("o.deleted_at IS NULL");

        String membershipSql = normalizedSql(configuration,
                "com.farmlog.common.tenant.FarmMembershipMapper.findActiveMemberRole",
                Map.of("farmId", 1L, "userId", 42L));
        assertThat(membershipSql)
                .contains("JOIN farm f ON f.id = fm.farm_id")
                .contains("f.deleted_at IS NULL");
    }

    @Test
    void schema_containsEveryColumnUsedByFarmZoneMappers() throws Exception {
        String schema = Files.readString(Path.of("database", "schema.sql"), StandardCharsets.UTF_8);
        String farmZoneTable = tableDefinition(schema, "farm_zone");
        String assignmentTable = tableDefinition(schema, "farm_zone_assignment");

        assertThat(farmZoneTable).contains(
                "organization_id BIGINT", "farm_id BIGINT", "zone_type VARCHAR(30)",
                "area_value DECIMAL(12,2)", "area_unit VARCHAR(20)", "display_order INT",
                "active_yn CHAR(1)", "deleted_at DATETIME(6)");
        assertThat(assignmentTable).contains(
                "organization_id BIGINT", "zone_id BIGINT", "farm_id BIGINT",
                "effective_from DATE", "effective_to DATE", "change_event_id BIGINT", "active_yn CHAR(1)");
    }

    private Configuration parseMappers(String... resources) throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : resources) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as("mapper resource %s", resource).isNotNull();
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    private String normalizedSql(Configuration configuration, String statementId, Object parameter) {
        BoundSql boundSql = configuration.getMappedStatement(statementId).getBoundSql(parameter);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }

    private String tableDefinition(String schema, String tableName) {
        int start = schema.indexOf("CREATE TABLE IF NOT EXISTS " + tableName + " (");
        assertThat(start).as("table %s", tableName).isGreaterThanOrEqualTo(0);
        int end = schema.indexOf(") ENGINE=InnoDB", start);
        assertThat(end).as("end of table %s", tableName).isGreaterThan(start);
        return schema.substring(start, end);
    }
}
