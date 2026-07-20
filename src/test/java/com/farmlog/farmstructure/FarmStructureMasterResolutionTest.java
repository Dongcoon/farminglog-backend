package com.farmlog.farmstructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farm.mapper.FarmZoneMapper;
import com.farmlog.farmstructure.dto.StructureDtos.Resolution;
import com.farmlog.farmstructure.dto.StructureDtos.Target;
import com.farmlog.farmstructure.entity.StructureItemRow;
import com.farmlog.farmstructure.mapper.FarmStructureMapper;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FarmStructureMasterResolutionTest {
  private FarmStructureMapper mapper;
  private StructureCanonicalJson json;
  private FarmStructureService service;

  @BeforeEach
  void setUp() {
    mapper = mock(FarmStructureMapper.class);
    json = new StructureCanonicalJson();
    service = new FarmStructureService(mapper, mock(FarmMapper.class), mock(FarmZoneMapper.class),
        mock(StructureAccessGuard.class), json);
  }

  @Test
  void reuseTargetReactivatesSoftDeletedCandidateAndMapsExactTargetId() throws Exception {
    Map<String,Object> source=row("CUSTOMER",1L,11L,"공판장","Y","N");
    Map<String,Object> target=row("CUSTOMER",3L,31L,"공판장","N","Y");
    Resolution resolution=new Resolution(conflictId("CUSTOMER:공판장",List.of(source)),"CUSTOMER",
        "REUSE_TARGET",31L,null,null);
    when(mapper.findMasterResources(any(),eq(List.of("CUSTOMER"))))
        .thenReturn(List.of(source,target));

    invoke(List.of(1L),3L,List.of("CUSTOMER"),List.of(resolution));

    verify(mapper).reactivateMasterResource(eq("CUSTOMER"),eq("공판장"),eq(3L),eq(31L),eq(7L),any());
    StructureItemRow item=capturedItem();
    assertThat(item.getAction()).isEqualTo("REUSE_TARGET");
    assertThat(item.getTargetResourceId()).isEqualTo(31L);
    assertThat(item.getAfterJson()).contains("\"reactivatedByEvent\":true");
  }

  @Test
  void copyRenamedCreatesLocalResourceAndRecordsResolvedName() throws Exception {
    Map<String,Object> source=row("WORK_TYPE",1L,12L,"선별","Y","N");
    Map<String,Object> targetConflict=row("WORK_TYPE",3L,32L,"선별","Y","N");
    Map<String,Object> renamed=row("WORK_TYPE",3L,33L,"선별-북동","Y","N");
    Resolution resolution=new Resolution(conflictId("WORK_TYPE:선별",List.of(source)),"WORK_TYPE",
        "COPY_RENAMED",null,null,"선별-북동");
    when(mapper.findMasterResources(any(),eq(List.of("WORK_TYPE"))))
        .thenReturn(List.of(source,targetConflict))
        .thenReturn(List.of(targetConflict))
        .thenReturn(List.of(targetConflict,renamed));

    invoke(List.of(1L),3L,List.of("WORK_TYPE"),List.of(resolution));

    verify(mapper).copyMasterResource(eq("WORK_TYPE"),eq("선별"),eq(1L),eq(12L),eq(3L),eq(9L),eq("선별-북동"),isNull(),eq(7L),any());
    StructureItemRow item=capturedItem();
    assertThat(item.getAction()).isEqualTo("COPY_RENAMED");
    assertThat(item.getTargetResourceId()).isEqualTo(33L);
    assertThat(item.getAfterJson()).contains("\"createdByEvent\":true","선별-북동");
  }

  @Test
  void canonicalSourceWinsNewTargetConflictAndSkipPerformsNoCopy() throws Exception {
    Map<String,Object> first=row("MATERIAL",1L,21L,"상토","Y","N");
    Map<String,Object> canonical=row("MATERIAL",2L,22L,"상토","Y","N");
    Map<String,Object> copied=row("MATERIAL",4L,44L,"상토","Y","N");
    String id=conflictId("MATERIAL:상토",List.of(first,canonical));
    Resolution canonicalResolution=new Resolution(id,"MATERIAL","REUSE_CANONICAL_SOURCE",null,22L,null);
    when(mapper.findMasterResources(any(),eq(List.of("MATERIAL"))))
        .thenReturn(List.of(first,canonical)).thenReturn(List.of(copied));
    invoke(List.of(1L,2L),4L,List.of("MATERIAL"),List.of(canonicalResolution));
    verify(mapper).copyMasterResource(eq("MATERIAL"),eq("상토"),eq(2L),eq(22L),eq(4L),eq(9L),eq("상토"),isNull(),eq(7L),any());

    mapper=mock(FarmStructureMapper.class);
    service=new FarmStructureService(mapper,mock(FarmMapper.class),mock(FarmZoneMapper.class),
        mock(StructureAccessGuard.class),json);
    Resolution skip=new Resolution(id,"MATERIAL","SKIP",null,null,null);
    when(mapper.findMasterResources(any(),eq(List.of("MATERIAL"))))
        .thenReturn(List.of(first,canonical));
    invoke(List.of(1L,2L),4L,List.of("MATERIAL"),List.of(skip));
    verify(mapper,never()).copyMasterResource(any(),any(),anyLong(),anyLong(),anyLong(),anyLong(),any(),any(),anyLong(),any());
    assertThat(capturedItem().getAction()).isEqualTo("SKIP");
  }

  @Test
  void representativeExistingTargetIsNotTreatedAsItsOwnSourceConflict() throws Exception {
    Map<String,Object> targetOnly=row("CUSTOMER",3L,31L,"공판장","Y","N");
    when(mapper.findMasterResources(any(),eq(List.of("CUSTOMER"))))
        .thenReturn(List.of(targetOnly));
    Method conflicts=FarmStructureService.class.getDeclaredMethod("masterConflicts",List.class,
        Target.class,List.class,List.class);
    conflicts.setAccessible(true);
    Object result=conflicts.invoke(service,List.of(1L,3L),new Target("EXISTING",3L,null,null),
        List.of("CUSTOMER"),List.of());
    assertThat((List<?>)result).isEmpty();

    invoke(List.of(1L,3L),3L,List.of("CUSTOMER"),List.of());
    verify(mapper,never()).insertItem(any());
  }

  @Test
  void copyRenamedRejectsNameAlreadyUsedByAnotherTargetResource() throws Exception {
    Map<String,Object> source=row("WORK_TYPE",1L,12L,"선별","Y","N");
    Map<String,Object> conflict=row("WORK_TYPE",3L,32L,"선별","Y","N");
    Map<String,Object> duplicate=row("WORK_TYPE",3L,33L,"선별-북동","Y","N");
    Resolution resolution=new Resolution(conflictId("WORK_TYPE:선별",List.of(source)),"WORK_TYPE",
        "COPY_RENAMED",null,null,"선별-북동");
    when(mapper.findMasterResources(any(),eq(List.of("WORK_TYPE"))))
        .thenReturn(List.of(source,conflict,duplicate));
    Method conflicts=FarmStructureService.class.getDeclaredMethod("masterConflicts",List.class,
        Target.class,List.class,List.class);
    conflicts.setAccessible(true);
    assertThatThrownBy(()->conflicts.invoke(service,List.of(1L),new Target("EXISTING",3L,null,null),
        List.of("WORK_TYPE"),List.of(resolution)))
        .hasRootCauseInstanceOf(com.farmlog.common.exception.BusinessException.class);

    mapper=mock(FarmStructureMapper.class);
    service=new FarmStructureService(mapper,mock(FarmMapper.class),mock(FarmZoneMapper.class),
        mock(StructureAccessGuard.class),json);
    when(mapper.findMasterResources(any(),eq(List.of("WORK_TYPE"))))
        .thenReturn(List.of(source,conflict,duplicate)).thenReturn(List.of(conflict,duplicate));
    assertThatThrownBy(()->invoke(List.of(1L),3L,List.of("WORK_TYPE"),List.of(resolution)))
        .hasRootCauseInstanceOf(com.farmlog.common.exception.BusinessException.class);
    verify(mapper,never()).copyMasterResource(any(),any(),anyLong(),anyLong(),anyLong(),anyLong(),any(),any(),anyLong(),any());
  }

  @Test
  void copyRenamedRejectsAnotherSourceGroupsDefaultDestinationForNewTarget() throws Exception {
    Map<String,Object> first=row("WORK_TYPE",1L,11L,"선별","Y","N");
    Map<String,Object> second=row("WORK_TYPE",2L,12L,"선별","Y","N");
    Map<String,Object> otherGroup=row("WORK_TYPE",1L,13L,"포장","Y","N");
    Resolution resolution=new Resolution(conflictId("WORK_TYPE:선별",List.of(first,second)),
        "WORK_TYPE","COPY_RENAMED",null,null,"포장");
    when(mapper.findMasterResources(any(),eq(List.of("WORK_TYPE"))))
        .thenReturn(List.of(first,second,otherGroup));

    assertThatThrownBy(()->invokeConflicts(List.of(1L,2L),new Target("NEW",null,"통합농장",7L),
        List.of("WORK_TYPE"),List.of(resolution)))
        .hasRootCauseInstanceOf(com.farmlog.common.exception.BusinessException.class);
  }

  @Test
  void copyRenamedRejectsDuplicateDestinationsAcrossResolutions() throws Exception {
    Map<String,Object> firstA=row("WORK_TYPE",1L,11L,"선별","Y","N");
    Map<String,Object> secondA=row("WORK_TYPE",2L,12L,"선별","Y","N");
    Map<String,Object> firstB=row("WORK_TYPE",1L,13L,"정식","Y","N");
    Map<String,Object> secondB=row("WORK_TYPE",2L,14L,"정식","Y","N");
    Resolution resolutionA=new Resolution(conflictId("WORK_TYPE:선별",List.of(firstA,secondA)),
        "WORK_TYPE","COPY_RENAMED",null,null,"통합작업");
    Resolution resolutionB=new Resolution(conflictId("WORK_TYPE:정식",List.of(firstB,secondB)),
        "WORK_TYPE","COPY_RENAMED",null,null,"통합작업");
    when(mapper.findMasterResources(any(),eq(List.of("WORK_TYPE"))))
        .thenReturn(List.of(firstA,secondA,firstB,secondB));

    assertThatThrownBy(()->invokeConflicts(List.of(1L,2L),new Target("NEW",null,"통합농장",7L),
        List.of("WORK_TYPE"),List.of(resolutionA,resolutionB)))
        .hasRootCauseInstanceOf(com.farmlog.common.exception.BusinessException.class);
  }

  @Test
  void nullResolutionActionAndResourceTypeAreValidationErrors() {
    Map<String,Object> first=row("WORK_TYPE",1L,11L,"선별","Y","N");
    Map<String,Object> second=row("WORK_TYPE",2L,12L,"선별","Y","N");
    String id=conflictId("WORK_TYPE:선별",List.of(first,second));
    when(mapper.findMasterResources(any(),eq(List.of("WORK_TYPE"))))
        .thenReturn(List.of(first,second));

    Resolution nullAction=new Resolution(id,"WORK_TYPE",null,null,null,null);
    assertThatThrownBy(()->invokeConflicts(List.of(1L,2L),new Target("NEW",null,"통합농장",7L),
        List.of("WORK_TYPE"),List.of(nullAction)))
        .hasRootCauseInstanceOf(com.farmlog.common.exception.BusinessException.class);

    Resolution nullResourceType=new Resolution(id,null,"SKIP",null,null,null);
    assertThatThrownBy(()->invokeConflicts(List.of(1L,2L),new Target("NEW",null,"통합농장",7L),
        List.of("WORK_TYPE"),List.of(nullResourceType)))
        .hasRootCauseInstanceOf(com.farmlog.common.exception.BusinessException.class);
  }

  private void invoke(List<Long> sources,Long target,List<String> scopes,List<Resolution> resolutions) throws Exception {
    Method method=FarmStructureService.class.getDeclaredMethod("applyMasterPlan",Long.class,List.class,
        Long.class,Long.class,List.class,List.class,Long.class,LocalDateTime.class);
    method.setAccessible(true);
    method.invoke(service,99L,sources,target,9L,scopes,resolutions,7L,LocalDateTime.of(2026,7,20,10,0));
  }

  private Object invokeConflicts(List<Long> sources,Target target,List<String> scopes,
      List<Resolution> resolutions) throws Exception {
    Method method=FarmStructureService.class.getDeclaredMethod("masterConflicts",List.class,
        Target.class,List.class,List.class);
    method.setAccessible(true);
    return method.invoke(service,sources,target,scopes,resolutions);
  }

  private StructureItemRow capturedItem(){
    ArgumentCaptor<StructureItemRow> captor=ArgumentCaptor.forClass(StructureItemRow.class);
    verify(mapper,org.mockito.Mockito.atLeastOnce()).insertItem(captor.capture());
    return captor.getAllValues().get(0);
  }

  private String conflictId(String key,List<Map<String,Object>> sources){
    return json.hash(Map.of("key",key,"sources",sources.stream()
        .map(row->List.of(row.get("farm_id"),row.get("resource_id"))).toList())).substring(0,24);
  }

  private Map<String,Object> row(String type,Long farm,Long id,String name,String active,String deleted){
    Map<String,Object> row=new LinkedHashMap<>();
    row.put("resource_type",type);row.put("farm_id",farm);row.put("resource_id",id);
    row.put("name",name);row.put("active",active);row.put("deleted",deleted);return row;
  }
}
