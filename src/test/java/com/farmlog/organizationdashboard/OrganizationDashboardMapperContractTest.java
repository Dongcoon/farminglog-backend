package com.farmlog.organizationdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class OrganizationDashboardMapperContractTest {
  private static final String NS="com.farmlog.organizationdashboard.mapper.OrganizationDashboardMapper.";

  @Test
  void accessQueryAllowsOnlyCurrentExactOrgAdmin() throws Exception {
    String xml=Files.readString(Path.of("src/main/resources/mapper/organizationdashboard/OrganizationAccessMapper.xml"),StandardCharsets.UTF_8);
    assertThat(xml).contains("om.role='ORG_ADMIN' AND om.status='ACTIVE'")
        .contains("o.status='ACTIVE' AND o.deleted_at IS NULL")
        .doesNotContain("SYSTEM_ADMIN","FARM_OWNER","FARM_MANAGER","farm_care_assignment");
  }

  @Test
  void detailBoundSqlPlacesCountColumnsBeforeFarmFromClause() throws Exception {
    Configuration configuration=parse("mapper/organizationdashboard/OrganizationDashboardMapper.xml");
    String sql=sql(configuration,"findFarmDetail",Map.of("organizationId",99L,"farmId",11L));
    assertThat(sql).contains("active_member_count","active_zone_count","FROM farm f")
        .doesNotContain("memo","phone","attachment");
    assertThat(sql.indexOf("active_member_count")).isLessThan(sql.indexOf("FROM farm f"));
    assertThat(sql.indexOf("active_zone_count")).isLessThan(sql.indexOf("FROM farm f"));
  }

  @Test
  void pageFiltersDataBeforeLimitAndUsesDeterministicMainCropAndTieBreaker() throws Exception {
    Configuration configuration=parse("mapper/organizationdashboard/OrganizationDashboardMapper.xml");
    Map<String,Object> p=new HashMap<>();p.put("organizationId",99L);p.put("q","%딸기%");
    p.put("lifecycleStatus","MERGED");p.put("status","ACTIVE");p.put("dataStatus","WITH_DATA");
    p.put("start",LocalDate.of(2026,7,1));p.put("endExclusive",LocalDate.of(2026,8,1));
    p.put("orderBy","f.name");p.put("direction","ASC");p.put("offset",0);p.put("size",20);
    String sql=sql(configuration,"findFarmPage",p);
    assertThat(sql).contains("f.deleted_at IS NULL","EXISTS(SELECT 1 FROM work_log",
        "ORDER BY fc2.display_order ASC,fc2.crop_id ASC LIMIT 1",
        "ORDER BY f.name ASC,f.id ASC LIMIT ? OFFSET ?");
    assertThat(sql.indexOf("EXISTS(SELECT 1 FROM work_log")).isLessThan(sql.lastIndexOf("LIMIT ? OFFSET ?"));
    String count=sql(configuration,"countFarmPage",p);
    assertThat(count).contains("EXISTS(SELECT 1 FROM work_log","f.lifecycle_status=?","f.status=?");
  }

  @Test
  void organizationScopeIncludesDeletedFilterButNotLifecycleAndMetricsAreBatchQueries() throws Exception {
    String xml=Files.readString(Path.of("src/main/resources/mapper/organizationdashboard/OrganizationDashboardMapper.xml"),StandardCharsets.UTF_8);
    int idsStart=xml.indexOf("<select id=\"findOrganizationFarmIds\"");
    int idsEnd=xml.indexOf("</select>",idsStart);
    String ids=xml.substring(idsStart,idsEnd);
    assertThat(ids).contains("deleted_at IS NULL").doesNotContain("lifecycle_status","status='ACTIVE'");
    assertThat(xml).contains("<foreach collection=\"farmIds\"")
        .contains("findRecordCounts","findHarvestComparison","findSalesComparison","findAverageUnitPrices")
        .contains("dataStatus=='NO_DATA'");
  }

  @Test
  void dtoAndAuditContractExcludeRawOperationalAndPersonalFields() throws Exception {
    String dto=Files.readString(Path.of("src/main/java/com/farmlog/organizationdashboard/dto/OrganizationDashboardDtos.java"),StandardCharsets.UTF_8);
    String service=Files.readString(Path.of("src/main/java/com/farmlog/organizationdashboard/OrganizationDashboardService.java"),StandardCharsets.UTF_8);
    assertThat(dto).doesNotContain("memo","phone","attachment","roster","rawEmail","address,");
    assertThat(service).contains("ORG_DASHBOARD_VIEW","ORG_FARM_LIST_VIEW","ORG_FARM_DETAIL_VIEW")
        .contains("@Transactional(isolation=Isolation.REPEATABLE_READ)")
        .doesNotContain("/admin/","AdminService","ReportService","ReportMapper");
    assertThat(count(service,"@Transactional(isolation=Isolation.REPEATABLE_READ)")).isEqualTo(3);
    assertThat(service.indexOf("LocalDateTime snapshotAt=LocalDateTime.now()"))
        .isLessThan(service.indexOf("guard.requireActiveOrgAdmin(actor,organizationId)"));
  }

  private Configuration parse(String resource)throws Exception{Configuration c=new Configuration();try(InputStream input=getClass().getClassLoader().getResourceAsStream(resource)){assertThat(input).isNotNull();new XMLMapperBuilder(input,c,resource,c.getSqlFragments()).parse();}return c;}
  private String sql(Configuration c,String id,Object p){BoundSql b=c.getMappedStatement(NS+id).getBoundSql(p);return b.getSql().replaceAll("\\s+"," ").trim();}
  private int count(String value,String token){int total=0,index=0;while((index=value.indexOf(token,index))>=0){total++;index+=token.length();}return total;}
}
