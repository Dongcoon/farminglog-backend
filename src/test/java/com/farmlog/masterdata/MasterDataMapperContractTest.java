package com.farmlog.masterdata;

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

import com.farmlog.masterdata.entity.CropSeasonEntity;
import com.farmlog.masterdata.mapper.MasterDataMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** MyBatis XML 파싱과 농장 조건/DDL 컬럼 계약을 실제 DB 없이 검증한다. */
class MasterDataMapperContractTest {
    private static final String NS = "com.farmlog.masterdata.mapper.MasterDataMapper.";

    @Test
    void mapperXml_parsesAndScopesEveryResourceMutationToFarm() throws Exception {
        Configuration configuration = parse("mapper/masterdata/MasterDataMapper.xml");
        Map<String, Object> p = new HashMap<>();
        p.put("farmId", 1L); p.put("id", 2L); p.put("cropId", 3L); p.put("varietyId", 4L);
        p.put("displayOrder", 0); p.put("activeYn", "N"); p.put("userId", 9L); p.put("now", null);
        p.put("name", "이름"); p.put("type", "OTHER"); p.put("phone", null); p.put("unit", null); p.put("memo", null);

        assertThat(sql(configuration, "updateFarmCrop", p)).contains("WHEREfarm_id=?ANDcrop_id=?");
        assertThat(sql(configuration, "updateFarmVariety", p)).contains("WHEREfarm_id=?ANDvariety_id=?");
        assertThat(sql(configuration, "updateWorkType", p)).contains("WHEREfarm_id=?ANDid=?");
        assertThat(sql(configuration, "updateCustomer", p)).contains("WHEREfarm_id=?ANDid=?ANDdeleted_atISNULL");
        assertThat(sql(configuration, "updateMaterial", p)).contains("WHEREfarm_id=?ANDid=?ANDdeleted_atISNULL");
        p.put("seasonId", 7L);
        assertThat(sql(configuration, "softDeleteCropSeason", p))
                .contains("UPDATEcrop_seasonSETdeleted_at=?").contains("WHEREfarm_id=?ANDid=?ANDdeleted_atISNULL");

        p.put("oldCropId", 3L); p.put("oldVarietyId", 4L); p.put("newCropId", 5L); p.put("newVarietyId", 6L);
        assertThat(sql(configuration, "migrateActiveSeasonsForCropVariety", p))
                .contains("WHEREfarm_id=?ANDcrop_id=?ANDvariety_id=?ANDstatus='ACTIVE'ANDdeleted_atISNULL");
        assertThat(sql(configuration, "migrateActiveSeasonsWithoutVariety", p))
                .contains("WHEREfarm_id=?ANDcrop_id=?ANDvariety_idISNULLANDstatus='ACTIVE'ANDdeleted_atISNULL");
        assertThat(sql(configuration, "migrateActiveSeasonsForVariety", p))
                .contains("WHEREfarm_id=?ANDcrop_id=?ANDvariety_id=?ANDstatus='ACTIVE'ANDdeleted_atISNULL");

        CropSeasonEntity season = new CropSeasonEntity();
        season.setId(7L); season.setFarmId(1L); season.setCropId(3L); season.setName("작기");
        Map<String, Object> seasonParams = new HashMap<>();
        seasonParams.put("season", season); seasonParams.put("userId", 9L); seasonParams.put("now", null);
        assertThat(sql(configuration, "updateCropSeason", seasonParams))
                .contains("WHEREfarm_id=?ANDid=?ANDdeleted_atISNULL");
    }

    @Test
    void migrationStatementsExposeAffectedRowCount() throws Exception {
        assertThat(MasterDataMapper.class.getMethod("migrateActiveSeasonsForCropVariety",
                Long.class, Long.class, Long.class, Long.class, Long.class, Long.class, java.time.LocalDateTime.class)
                .getReturnType()).isEqualTo(int.class);
        assertThat(MasterDataMapper.class.getMethod("migrateActiveSeasonsWithoutVariety",
                Long.class, Long.class, Long.class, Long.class, java.time.LocalDateTime.class)
                .getReturnType()).isEqualTo(int.class);
        assertThat(MasterDataMapper.class.getMethod("migrateActiveSeasonsForVariety",
                Long.class, Long.class, Long.class, Long.class, Long.class, java.time.LocalDateTime.class)
                .getReturnType()).isEqualTo(int.class);
    }

    @Test
    void schema_containsImmutableCatalogLinksAndLocalUniqueness() throws Exception {
        String schema = Files.readString(Path.of("database", "schema.sql"), StandardCharsets.UTF_8);
        assertThat(table(schema, "farm_crop")).contains("UNIQUE KEY uk_farm_crop (farm_id, crop_id)", "active_yn CHAR(1)");
        assertThat(table(schema, "farm_crop_variety")).contains(
                "UNIQUE KEY uk_farm_crop_variety (farm_id, variety_id)", "crop_id BIGINT", "active_yn CHAR(1)");
        assertThat(table(schema, "work_type")).contains("created_by BIGINT", "updated_at DATETIME(6)",
                "UNIQUE KEY uk_work_type_farm_name (farm_id, name)");
        assertThat(table(schema, "customer")).contains("UNIQUE KEY uk_customer_farm_name (farm_id, name)");
        assertThat(table(schema, "material")).contains("UNIQUE KEY uk_material_farm_name (farm_id, name)");
        assertThat(table(schema, "crop_season")).contains(
                "farm_id BIGINT", "crop_id BIGINT", "variety_id BIGINT", "status VARCHAR(30)",
                "updated_by BIGINT", "updated_at DATETIME(6)", "deleted_at DATETIME(6)");
    }

    @Test
    void seedData_isUtf8AndTypoWasRemoved() throws Exception {
        String seed = Files.readString(Path.of("database", "seed-data.sql"), StandardCharsets.UTF_8);
        assertThat(seed).contains("딸기", "설향", "서울공판장").doesNotContain("서울common공판장", "�");
    }

    private Configuration parse(String resource) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
    private String sql(Configuration c, String id, Object p) {
        BoundSql boundSql = c.getMappedStatement(NS + id).getBoundSql(p);
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
