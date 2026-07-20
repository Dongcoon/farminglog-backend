package com.farmlog.report;

import com.farmlog.common.exception.*;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmstructure.StructureAccessGuard;
import com.farmlog.farmstructure.entity.*;
import com.farmlog.farmstructure.mapper.FarmStructureMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectedReportServiceTest {
  @Mock FarmStructureMapper mapper; @Mock FarmMapper farms; @Mock StructureAccessGuard access;
  ProjectedReportService service;
  @BeforeEach void setUp(){service=new ProjectedReportService(mapper,farms,access);lenient().when(farms.findById(2L)).thenReturn(Optional.of(FarmEntity.builder().id(2L).organizationId(10L).name("통합 농장").build()));}

  @Test void currentEchoesLatestCancelTupleMarkerEvenWhenNoOriginalItemsRemain(){
    StructureEventRow marker=event(9L,"CANCEL");when(mapper.findLastAppliedEvent(10L)).thenReturn(Optional.of(marker));
    when(mapper.findProjectionItems(eq(10L),any(),eq(9L),eq(true))).thenReturn(List.of());
    var result=service.monthly(7L,2L,YearMonth.of(2026,7),"CURRENT_STRUCTURE",null);
    assertThat(result.recordCounts().harvest()).isZero();
    assertThat(result.structureSnapshotEventId()).isEqualTo(9L);
    assertThat(result.structureSnapshotAt()).isEqualTo(marker.getConfirmedAt());
  }

  @Test void mergeThenSplitReplayHonorsEffectiveDateBoundary(){
    StructureEventRow marker=event(8L,"SPLIT");when(mapper.findLastAppliedEvent(10L)).thenReturn(Optional.of(marker));
    StructureItemRow merge=item("MERGE","FARM",1L,2L,null,"ALL_HISTORY",null,null);
    StructureItemRow split=item("SPLIT","ZONE",2L,3L,5L,"FROM_EFFECTIVE_DATE",LocalDate.of(2026,7,10),null);
    when(mapper.findProjectionItems(eq(10L),any(),eq(8L),eq(true))).thenReturn(List.of(merge,split));
    ProjectedRecordRow before=record("HARVEST",1L,LocalDate.of(2026,7,9));before.setZoneId(5L);before.setUnit("kg");before.setQuantity(BigDecimal.ONE);
    ProjectedRecordRow boundary=record("HARVEST",1L,LocalDate.of(2026,7,10));boundary.setZoneId(5L);boundary.setUnit("kg");boundary.setQuantity(BigDecimal.ONE);
    when(mapper.findProjectionRecords(eq(10L),any(),any())).thenReturn(List.of(before,boundary));
    when(farms.findById(3L)).thenReturn(Optional.of(FarmEntity.builder().id(3L).organizationId(10L).name("분리 농장").build()));
    assertThat(service.monthly(7L,3L,YearMonth.of(2026,7),"CURRENT_STRUCTURE",null).recordCounts().harvest()).isEqualTo(1);
  }

  @Test void selectedPeriodIsHalfOpenAndRecordedOnlyNeverProjects(){
    when(mapper.findLastAppliedEvent(10L)).thenReturn(Optional.of(event(6L,"MERGE")));
    StructureItemRow selected=item("MERGE","FARM",1L,2L,null,"SELECTED_PERIOD",null,LocalDate.of(2026,7,20));
    selected.setPolicyStart(LocalDate.of(2026,7,10));
    StructureItemRow recorded=item("MERGE","FARM",4L,2L,null,"RECORDED_ONLY",null,null);
    when(mapper.findProjectionItems(eq(10L),any(),eq(6L),eq(true))).thenReturn(List.of(selected,recorded));
    List<ProjectedRecordRow> rows=List.of(record("WORK",1L,LocalDate.of(2026,7,10)),record("WORK",1L,LocalDate.of(2026,7,20)),record("WORK",4L,LocalDate.of(2026,7,15)));
    when(mapper.findProjectionRecords(eq(10L),any(),any())).thenReturn(rows);
    assertThat(service.monthly(7L,2L,YearMonth.of(2026,7),"CURRENT_STRUCTURE",null).recordCounts().work()).isEqualTo(1);
  }

  @Test void authorizedDraftReportsConflictAfterAuthorization(){
    StructureEventRow draft=event(4L,"MERGE");draft.setConfirmedAt(null);draft.setStatus("DRAFT");
    draft.setRequestSnapshotJson("{\"sourceFarmIds\":[1],\"target\":{\"mode\":\"NEW\"}}");
    when(mapper.findEvent(4L)).thenReturn(Optional.of(draft));
    when(mapper.findItems(4L)).thenReturn(List.of());
    assertThatThrownBy(()->service.monthly(7L,2L,YearMonth.of(2026,7),"EVENT",4L))
        .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    verify(access).requireAll(7L,10L,List.of(1L));
  }

  @Test void zeroSalesQuantityDoesNotCreateAveragePriceOrDivideByZero(){
    when(mapper.findLastAppliedEvent(10L)).thenReturn(Optional.empty());
    ProjectedRecordRow sales=record("SALES",2L,LocalDate.of(2026,7,5));sales.setUnit("kg");sales.setQuantity(BigDecimal.ZERO);sales.setGrossAmount(BigDecimal.TEN);
    when(mapper.findProjectionRecords(eq(10L),any(),any())).thenReturn(List.of(sales));
    assertThat(service.monthly(7L,2L,YearMonth.of(2026,7),"CURRENT_STRUCTURE",null).averageUnitPrices()).isEmpty();
  }

  @Test void splitNeverMovesZoneLessSales(){
    when(mapper.findLastAppliedEvent(10L)).thenReturn(Optional.of(event(3L,"SPLIT")));
    StructureItemRow split=new StructureItemRow();split.setEventType("SPLIT");split.setItemType("ZONE");split.setZoneId(5L);split.setSourceFarmId(1L);split.setTargetFarmId(2L);split.setReportPolicy("ALL_HISTORY");
    when(mapper.findProjectionItems(eq(10L),any(),eq(3L),eq(true))).thenReturn(List.of(split));
    ProjectedRecordRow sales=record("SALES",1L,LocalDate.of(2026,7,5));sales.setUnit("kg");sales.setQuantity(BigDecimal.ONE);
    when(mapper.findProjectionRecords(eq(10L),any(),any())).thenReturn(List.of(sales));
    assertThat(service.monthly(7L,2L,YearMonth.of(2026,7),"CURRENT_STRUCTURE",null).recordCounts().sales()).isZero();
  }

  @Test void unauthorizedEventIsHiddenBeforeDraftStateIsReported(){
    StructureEventRow draft=event(4L,"MERGE");draft.setConfirmedAt(null);draft.setStatus("DRAFT");
    draft.setRequestSnapshotJson("{\"sourceFarmIds\":[99],\"target\":{\"mode\":\"NEW\"}}");
    when(mapper.findEvent(4L)).thenReturn(Optional.of(draft));
    when(mapper.findItems(4L)).thenReturn(List.of());
    doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(access).requireAll(7L,10L,List.of(99L));
    assertThatThrownBy(()->service.monthly(7L,2L,YearMonth.of(2026,7),"EVENT",4L))
        .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND));
  }

  private StructureEventRow event(Long id,String type){StructureEventRow e=new StructureEventRow();e.setId(id);e.setOrganizationId(10L);e.setEventType(type);e.setStatus("CONFIRMED");e.setConfirmedAt(LocalDateTime.of(2026,7,20,10,0));return e;}
  private StructureItemRow item(String eventType,String itemType,Long source,Long target,Long zone,String policy,LocalDate effective,LocalDate end){StructureItemRow i=new StructureItemRow();i.setEventType(eventType);i.setItemType(itemType);i.setSourceFarmId(source);i.setTargetFarmId(target);i.setZoneId(zone);i.setReportPolicy(policy);i.setEventEffectiveDate(effective);i.setPolicyEndExclusive(end);return i;}
  private ProjectedRecordRow record(String type,Long farm,LocalDate date){ProjectedRecordRow r=new ProjectedRecordRow();r.setRecordType(type);r.setFarmId(farm);r.setRecordDate(date);r.setCreatedRole("FARM_OWNER");return r;}
}
