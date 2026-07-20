package com.farmlog.organizationdashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.organizationdashboard.dto.OrganizationDashboardDtos.ChangeStatus;
import com.farmlog.organizationdashboard.entity.*;
import com.farmlog.organizationdashboard.mapper.OrganizationDashboardMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrganizationDashboardServiceTest {
  private OrganizationDashboardMapper mapper;
  private OrganizationAccessGuard guard;
  private OrganizationDashboardService service;

  @BeforeEach
  void setUp() {
    mapper=mock(OrganizationDashboardMapper.class);guard=mock(OrganizationAccessGuard.class);
    service=new OrganizationDashboardService(mapper,guard,new ObjectMapper().findAndRegisterModules());
    OrganizationAccessRow organization=new OrganizationAccessRow();organization.setId(99L);
    organization.setName("딸기 작목반");organization.setOrgType("FARM_GROUP");organization.setStatus("ACTIVE");
    when(guard.requireActiveOrgAdmin(7L,99L)).thenReturn(organization);
    when(mapper.findRecordCounts(anyList(),any(),any())).thenReturn(List.of());
    when(mapper.findHarvestComparison(anyList(),any(),any(),any())).thenReturn(List.of());
    when(mapper.findSalesComparison(anyList(),any(),any(),any())).thenReturn(List.of());
    when(mapper.findAverageUnitPrices(anyList(),any(),any())).thenReturn(List.of());
  }

  @Test
  void zeroFarmDashboardReturnsZeroMetricsWithoutInvalidInQueryAndAuditsOnce() {
    when(mapper.findOrganizationFarmIds(99L)).thenReturn(List.of());

    var response=service.dashboard(7L,99L,"2026-07","RECORDED_STRUCTURE");

    assertThat(response.farmCount()).isZero();assertThat(response.activeFarmCount()).isZero();
    assertThat(response.includedRecordCount()).isZero();
    assertThat(response.changes().grossSales().status()).isEqualTo(ChangeStatus.NO_DATA);
    verify(mapper,never()).findRecordCounts(anyList(),any(),any());
    verify(mapper,times(1)).insertAudit(eq(99L),isNull(),eq(7L),eq("ORG_DASHBOARD_VIEW"),
        eq("ORGANIZATION"),eq(99L),anyString(),any());
  }

  @Test
  void dashboardKeepsMergedFarmRecordsAndCalculatesWeightedAverageAndPreviousChanges() {
    when(mapper.findOrganizationFarmIds(99L)).thenReturn(List.of(11L,12L));
    when(mapper.countActiveFarms(99L)).thenReturn(1L);
    when(mapper.findRecordCounts(anyList(),any(),any())).thenReturn(List.of(
        count(11L,"HARVEST",1,0),count(11L,"SALES",1,1),count(12L,"SALES",1,0)));
    when(mapper.findHarvestComparison(anyList(),any(),any(),any())).thenReturn(List.of(
        harvest(11L,"kg","3","1",1,1),harvest(12L,"kg","2","0",1,0)));
    when(mapper.findSalesComparison(anyList(),any(),any(),any())).thenReturn(List.of(
        sales(11L,"30","20",1,1),sales(12L,"20","0",1,0)));
    when(mapper.findAverageUnitPrices(anyList(),any(),any())).thenReturn(List.of(
        average(11L,"kg","3","30"),average(12L,"kg","2","20")));

    var response=service.dashboard(7L,99L,"2026-07","RECORDED_STRUCTURE");

    assertThat(response.farmCount()).isEqualTo(2);assertThat(response.activeFarmCount()).isEqualTo(1);
    assertThat(response.farmsWithDataCount()).isEqualTo(2);
    assertThat(response.averageUnitPrices().get(0).averageUnitPrice()).isEqualByComparingTo("10.00");
    assertThat(response.changes().grossSales().current()).isEqualByComparingTo("50.00");
    assertThat(response.changes().grossSales().previous()).isEqualByComparingTo("20.00");
    assertThat(response.changes().harvestByUnit().get(0).quantity()).isEqualByComparingTo("5.00");
    verify(mapper).findRecordCounts(eq(List.of(11L,12L)),eq(LocalDate.of(2026,7,1)),eq(LocalDate.of(2026,8,1)));
  }

  @Test
  void farmListUsesPageIdsForOneBatchAndDoesNotLoadAveragePrices() {
    OrganizationFarmRow row=farm(11L,"MERGED");row.setAddress("전북 익산시 금마면 1");
    row.setOwnerEmail("owner@example.com");row.setMainCropId(3L);row.setMainCropName("딸기");
    when(mapper.findFarmPage(anyLong(),any(),any(),any(),any(),any(),any(),any(),any(),anyInt(),anyInt()))
        .thenReturn(List.of(row));
    when(mapper.countFarmPage(anyLong(),any(),any(),any(),any(),any(),any())).thenReturn(7L);
    when(mapper.findRecordCounts(anyList(),any(),any())).thenReturn(List.of(count(11L,"WORK",2,1)));

    var response=service.farms(7L,99L,new OrganizationFarmFilter("2026-07","RECORDED_STRUCTURE",
        "  A%_\\  ","MERGED","ACTIVE","WITH_DATA",1,2,"name,asc"));

    assertThat(response.totalElements()).isEqualTo(7);assertThat(response.page()).isEqualTo(1);
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).addressSummary()).isEqualTo("전북 익산시 ***");
    assertThat(response.content().get(0).owner().maskedEmail()).isEqualTo("o***@example.com");
    assertThat(response.content().get(0).includedRecordCount()).isEqualTo(2);
    verify(mapper,times(1)).findRecordCounts(eq(List.of(11L)),any(),any());
    verify(mapper,times(1)).findHarvestComparison(eq(List.of(11L)),any(),any(),any());
    verify(mapper,times(1)).findSalesComparison(eq(List.of(11L)),any(),any(),any());
    verify(mapper,never()).findAverageUnitPrices(anyList(),any(),any());
    ArgumentCaptor<String> q=ArgumentCaptor.forClass(String.class);
    verify(mapper).findFarmPage(eq(99L),q.capture(),eq("MERGED"),eq("ACTIVE"),eq("WITH_DATA"),
        any(),any(),eq("f.name"),eq("ASC"),eq(2),eq(2));
    assertThat(q.getValue()).isEqualTo("%a\\%\\_\\\\%");
    ArgumentCaptor<String> audit=ArgumentCaptor.forClass(String.class);
    verify(mapper).insertAudit(eq(99L),isNull(),eq(7L),eq("ORG_FARM_LIST_VIEW"),any(),any(),audit.capture(),any());
    assertThat(audit.getValue()).doesNotContain("A%_","address").contains("qPresent");
  }

  @Test
  void detailAllowsMergedFarmButHidesRawPersonalAndOperationalFields() {
    OrganizationFarmRow row=farm(11L,"MERGED");row.setAddress("충남 논산시 연무읍 2");
    row.setOwnerEmail("farmer@example.com");row.setActiveMemberCount(3);row.setActiveZoneCount(2);
    when(mapper.findFarmDetail(99L,11L)).thenReturn(Optional.of(row));
    when(mapper.findRecordCounts(anyList(),any(),any())).thenReturn(List.of(count(11L,"PEST_CONTROL",2,1)));
    when(mapper.findAverageUnitPrices(anyList(),any(),any())).thenReturn(List.of(average(11L,"box","4","10")));

    var response=service.detail(7L,99L,11L,"2026-07","RECORDED_STRUCTURE");

    assertThat(response.farm().lifecycleStatus()).isEqualTo("MERGED");
    assertThat(response.farm().addressSummary()).isEqualTo("충남 논산시 ***");
    assertThat(response.farm().owner().maskedEmail()).isEqualTo("f***@example.com");
    assertThat(response.farm().activeMemberCount()).isEqualTo(3);
    assertThat(response.monthly().averageUnitPrices().get(0).averageUnitPrice()).isEqualByComparingTo("2.50");
    verify(mapper,times(1)).insertAudit(eq(99L),eq(11L),eq(7L),eq("ORG_FARM_DETAIL_VIEW"),
        eq("FARM"),eq(11L),anyString(),any());
  }

  @Test
  void foreignFarmIsHiddenAndInvalidInputsAreValidationErrors() {
    when(mapper.findFarmDetail(99L,88L)).thenReturn(Optional.empty());
    assertThatThrownBy(()->service.detail(7L,99L,88L,"2026-07","RECORDED_STRUCTURE"))
        .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FARM_NOT_FOUND));
    assertThatThrownBy(()->service.detail(7L,99L,0L,"2026-07","RECORDED_STRUCTURE"))
        .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThatThrownBy(()->service.dashboard(7L,0L,"2026-07","RECORDED_STRUCTURE"))
        .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    for(OrganizationFarmFilter filter:List.of(
        new OrganizationFarmFilter("2026/07","RECORDED_STRUCTURE",null,null,null,"ALL",0,20,"name,asc"),
        new OrganizationFarmFilter("2026-07",null,null,null,null,"ALL",0,20,"name,asc"),
        new OrganizationFarmFilter("2026-07","RECORDED_STRUCTURE",null,"DELETED",null,"ALL",0,20,"name,asc"),
        new OrganizationFarmFilter("2026-07","RECORDED_STRUCTURE",null,null,"DELETED","ALL",0,20,"name,asc"),
        new OrganizationFarmFilter("2026-07","RECORDED_STRUCTURE",null,null,null,null,0,20,"name,asc"),
        new OrganizationFarmFilter("2026-07","RECORDED_STRUCTURE",null,null,null,"ALL",-1,20,"name,asc"),
        new OrganizationFarmFilter("2026-07","RECORDED_STRUCTURE","가".repeat(101),null,null,"ALL",0,20,"name,asc"),
        new OrganizationFarmFilter("2026-07","RECORDED_STRUCTURE",null,null,null,"ALL",0,20,null))) {
      assertThatThrownBy(()->service.farms(7L,99L,filter)).isInstanceOfSatisfying(BusinessException.class,
          e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
  }

  private OrganizationFarmRow farm(Long id,String lifecycle){OrganizationFarmRow r=new OrganizationFarmRow();r.setId(id);r.setFarmCode("F-"+id);r.setName("테스트 농장");r.setLifecycleStatus(lifecycle);r.setStatus("ACTIVE");r.setOwnerUserId(5L);r.setOwnerDisplayName("농장주");r.setCreatedAt(LocalDateTime.of(2026,1,1,0,0));return r;}
  private OrganizationRecordCountRow count(Long farm,String type,long count,long manager){OrganizationRecordCountRow r=new OrganizationRecordCountRow();r.setFarmId(farm);r.setRecordType(type);r.setRecordCount(count);r.setManagerInputCount(manager);return r;}
  private OrganizationHarvestRow harvest(Long farm,String unit,String current,String previous,long cc,long pc){OrganizationHarvestRow r=new OrganizationHarvestRow();r.setFarmId(farm);r.setUnit(unit);r.setCurrentQuantity(new BigDecimal(current));r.setPreviousQuantity(new BigDecimal(previous));r.setCurrentCount(cc);r.setPreviousCount(pc);return r;}
  private OrganizationSalesRow sales(Long farm,String current,String previous,long cc,long pc){OrganizationSalesRow r=new OrganizationSalesRow();r.setFarmId(farm);r.setCurrentGross(new BigDecimal(current));r.setCurrentFee(BigDecimal.ZERO);r.setCurrentNet(new BigDecimal(current));r.setPreviousGross(new BigDecimal(previous));r.setPreviousFee(BigDecimal.ZERO);r.setPreviousNet(new BigDecimal(previous));r.setCurrentCount(cc);r.setPreviousCount(pc);return r;}
  private OrganizationAverageRow average(Long farm,String unit,String quantity,String gross){OrganizationAverageRow r=new OrganizationAverageRow();r.setFarmId(farm);r.setUnit(unit);r.setSoldQuantity(new BigDecimal(quantity));r.setGrossSalesAmount(new BigDecimal(gross));return r;}
}
