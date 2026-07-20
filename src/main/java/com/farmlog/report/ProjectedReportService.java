package com.farmlog.report;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmstructure.entity.ProjectedRecordRow;
import com.farmlog.farmstructure.entity.StructureEventRow;
import com.farmlog.farmstructure.entity.StructureItemRow;
import com.farmlog.farmstructure.mapper.FarmStructureMapper;
import com.farmlog.farmstructure.StructureAccessGuard;
import com.farmlog.report.dto.MonthlyReportResponse;
import org.springframework.stereotype.Service;

import java.math.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** CURRENT/EVENT는 immutable 원시 행에 event chain을 replay한 뒤 한 번만 집계한다. */
@Service
public class ProjectedReportService {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final FarmStructureMapper mapper; private final FarmMapper farms; private final StructureAccessGuard access;
  public ProjectedReportService(FarmStructureMapper mapper,FarmMapper farms,StructureAccessGuard access){this.mapper=mapper;this.farms=farms;this.access=access;}

  public MonthlyReportResponse monthly(Long userId,Long farmId,YearMonth month,String basis,Long eventId){
    return monthly(userId,farmId,month,basis,eventId,LocalDateTime.now());
  }

  public MonthlyReportResponse monthly(Long userId,Long farmId,YearMonth month,String basis,Long eventId,LocalDateTime querySnapshot){
    FarmEntity farm=farms.findById(farmId).orElseThrow(()->new BusinessException(ErrorCode.FARM_NOT_FOUND));
    StructureEventRow cutoff=null; boolean current="CURRENT_STRUCTURE".equals(basis);
    if("EVENT".equals(basis)){
      cutoff=mapper.findEvent(eventId).filter(e->Objects.equals(e.getOrganizationId(),farm.getOrganizationId())).orElseThrow(()->new BusinessException(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND));
      List<StructureItemRow> relationItems = new ArrayList<>(mapper.findItems(eventId));
      if ("CANCEL".equals(cutoff.getEventType()) && cutoff.getReversesEventId() != null) {
        relationItems.addAll(mapper.findItems(cutoff.getReversesEventId()));
      }
      List<Long> related=relationItems.stream().flatMap(i->java.util.stream.Stream.of(i.getSourceFarmId(),i.getTargetFarmId())).filter(Objects::nonNull).distinct().toList();
      if(related.isEmpty()) related=relatedFromSnapshot(cutoff.getRequestSnapshotJson());
      try{access.requireAll(userId,farm.getOrganizationId(),related);}catch(BusinessException denied){throw new BusinessException(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND);}
      if(cutoff.getConfirmedAt()==null||Set.of("DRAFT","EXPIRED").contains(cutoff.getStatus()))throw new BusinessException(ErrorCode.CONFLICT,"확정된 이벤트만 리포트 기준으로 사용할 수 있습니다.");
    }else cutoff=mapper.findLastAppliedEvent(farm.getOrganizationId()).orElse(null);
    List<StructureItemRow> items=mapper.findProjectionItems(farm.getOrganizationId(),cutoff==null?querySnapshot:cutoff.getConfirmedAt(),cutoff==null?Long.MAX_VALUE:cutoff.getId(),current);
    LocalDate start=month.atDay(1),end=month.plusMonths(1).atDay(1),previous=month.minusMonths(1).atDay(1);
    List<Projected> rows=mapper.findProjectionRecords(farm.getOrganizationId(),previous,end).stream().map(r->new Projected(r,project(r,items))).toList();
    Acc now=aggregate(rows,farmId,start,end),before=aggregate(rows,farmId,previous,start);
    LocalDateTime snapAt=cutoff==null?querySnapshot:cutoff.getConfirmedAt();Long snapId=cutoff==null?null:cutoff.getId();
    return now.response(farm,month,basis,eventId,snapAt,snapId,before);
  }

  private List<Long> relatedFromSnapshot(String snapshot){
    if(snapshot==null)return List.of();
    try{
      Map<String,Object> root=JSON.readValue(snapshot,new TypeReference<>(){});
      List<Long> ids=new ArrayList<>();
      if(root.get("sourceFarmIds") instanceof List<?> many)many.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).forEach(ids::add);
      if(root.get("sourceFarmId") instanceof Number one)ids.add(one.longValue());
      if(root.get("target") instanceof Map<?,?> target&&target.get("farmId") instanceof Number targetId)ids.add(targetId.longValue());
      return ids.stream().distinct().sorted().toList();
    }catch(Exception invalid){return List.of();}
  }

  private Long project(ProjectedRecordRow r,List<StructureItemRow> items){Long projected=r.getFarmId();for(StructureItemRow i:items){if(!applies(i,r.getRecordDate()))continue;if("MERGE".equals(i.getEventType())&&"FARM".equals(i.getItemType())&&Objects.equals(projected,i.getSourceFarmId())&&!Objects.equals(i.getSourceFarmId(),i.getTargetFarmId()))projected=i.getTargetFarmId();else if("SPLIT".equals(i.getEventType())&&"ZONE".equals(i.getItemType())&&r.getZoneId()!=null&&Objects.equals(r.getZoneId(),i.getZoneId())&&Objects.equals(projected,i.getSourceFarmId()))projected=i.getTargetFarmId();}return projected;}
  private boolean applies(StructureItemRow i,LocalDate d){return switch(i.getReportPolicy()){case "RECORDED_ONLY"->false;case "FROM_EFFECTIVE_DATE"->!d.isBefore(i.getEventEffectiveDate());case "SELECTED_PERIOD"->!d.isBefore(i.getPolicyStart())&&d.isBefore(i.getPolicyEndExclusive());case "ALL_HISTORY"->true;default->false;};}
  private Acc aggregate(List<Projected> rows,Long farm,LocalDate from,LocalDate to){Acc a=new Acc();rows.stream().filter(x->Objects.equals(x.farmId,farm)&&!x.row.getRecordDate().isBefore(from)&&x.row.getRecordDate().isBefore(to)).forEach(a::add);return a;}
  private record Projected(ProjectedRecordRow row,Long farmId){}

  private static final class Acc {
    long work,pest,harvest,sales,manager;BigDecimal gross=BigDecimal.ZERO,fee=BigDecimal.ZERO,net=BigDecimal.ZERO;
    final Map<String,BigDecimal> harvestUnits=new TreeMap<>(),soldUnits=new TreeMap<>(),grossUnits=new TreeMap<>();
    final Map<Key,Group> byZone=new LinkedHashMap<>(),byVariety=new LinkedHashMap<>();final Map<Key,Customer> byCustomer=new LinkedHashMap<>();
    void add(Projected p){ProjectedRecordRow r=p.row;if("FARM_CARE_MANAGER".equals(r.getCreatedRole()))manager++;switch(r.getRecordType()){case "WORK"->work++;case "PEST_CONTROL"->pest++;case "HARVEST"->{harvest++;harvestUnits.merge(unit(r),z(r.getQuantity()),BigDecimal::add);group(byZone,r.getZoneBreakdownId(),r.getZoneName(),r).add(r);group(byVariety,r.getVarietyId(),r.getVarietyName(),r).add(r);}case "SALES"->{sales++;gross=gross.add(z(r.getGrossAmount()));fee=fee.add(z(r.getFeeAmount()));net=net.add(z(r.getNetAmount()));soldUnits.merge(unit(r),z(r.getQuantity()),BigDecimal::add);grossUnits.merge(unit(r),z(r.getGrossAmount()),BigDecimal::add);Customer c=byCustomer.computeIfAbsent(new Key(r.getCustomerId(),name(r.getCustomerName(),"미지정")),k->new Customer());c.add(r);}}}
    private Group group(Map<Key,Group> map,Long id,String name,ProjectedRecordRow r){return map.computeIfAbsent(new Key(id,name(name,"미지정")),k->new Group());}
    MonthlyReportResponse response(FarmEntity farm,YearMonth month,String basis,Long eventId,LocalDateTime snapshot,Long snapshotId,Acc prev){
      List<MonthlyReportResponse.UnitQuantity> harvestBy=harvestUnits.entrySet().stream().map(e->new MonthlyReportResponse.UnitQuantity(e.getKey(),q(e.getValue()))).toList();
      Set<String> hu=new TreeSet<>();hu.addAll(harvestUnits.keySet());hu.addAll(prev.harvestUnits.keySet());
      List<MonthlyReportResponse.HarvestChange> hc=hu.stream().map(u->new MonthlyReportResponse.HarvestChange(u,q(harvestUnits.get(u)),change(harvestUnits.get(u),prev.harvestUnits.get(u),harvestUnits.containsKey(u),prev.harvestUnits.containsKey(u)))).toList();
      List<MonthlyReportResponse.AverageUnitPrice> avg=soldUnits.keySet().stream()
          .filter(u->q(soldUnits.get(u)).signum()>0)
          .map(u->new MonthlyReportResponse.AverageUnitPrice(u,q(soldUnits.get(u)),money(grossUnits.get(u)),money(grossUnits.get(u)).divide(q(soldUnits.get(u)),2,RoundingMode.HALF_UP))).toList();
      return new MonthlyReportResponse(farm.getId(),farm.getName(),month.toString(),month.atDay(1),month.atEndOfMonth(),basis,eventId,snapshot,snapshotId,work+pest+harvest+sales,new MonthlyReportResponse.RecordCounts(work,pest,harvest,sales),harvestBy,money(gross),money(fee),money(net),avg,new MonthlyReportResponse.Changes(hc,change(gross,prev.gross,sales>0,prev.sales>0),change(net,prev.net,sales>0,prev.sales>0)),breakdowns(byZone),breakdowns(byVariety),customers(),manager);
    }
    private List<MonthlyReportResponse.HarvestBreakdown> breakdowns(Map<Key,Group> map){return map.entrySet().stream().sorted(Map.Entry.comparingByKey(KEY_ORDER)).map(e->new MonthlyReportResponse.HarvestBreakdown(e.getKey().id,e.getKey().name,e.getValue().units.entrySet().stream().map(x->new MonthlyReportResponse.UnitQuantity(x.getKey(),q(x.getValue()))).toList(),e.getValue().count,e.getValue().manager)).toList();}
    private List<MonthlyReportResponse.CustomerBreakdown> customers(){return byCustomer.entrySet().stream().sorted(Map.Entry.comparingByKey(KEY_ORDER)).map(e->new MonthlyReportResponse.CustomerBreakdown(e.getKey().id,e.getKey().name,money(e.getValue().gross),money(e.getValue().fee),money(e.getValue().net),e.getValue().count,e.getValue().manager)).toList();}
    private static MonthlyReportResponse.Change change(BigDecimal c,BigDecimal p,boolean cd,boolean pd){c=money(c);p=money(p);BigDecimal abs=money(c.subtract(p));if(!cd&&!pd)return new MonthlyReportResponse.Change(c,p,abs,null,MonthlyReportResponse.ChangeStatus.NO_DATA);if(p.signum()==0&&c.signum()>0)return new MonthlyReportResponse.Change(c,p,abs,null,MonthlyReportResponse.ChangeStatus.NEW);int x=c.compareTo(p);return new MonthlyReportResponse.Change(c,p,abs,p.signum()==0?null:abs.multiply(BigDecimal.valueOf(100)).divide(p,2,RoundingMode.HALF_UP),x>0?MonthlyReportResponse.ChangeStatus.UP:x<0?MonthlyReportResponse.ChangeStatus.DOWN:MonthlyReportResponse.ChangeStatus.SAME);}
    private static String unit(ProjectedRecordRow r){return name(r.getUnit(),"미지정").trim().toLowerCase(Locale.ROOT);}private static String name(String x,String d){return x==null||x.isBlank()?d:x;}private static BigDecimal z(BigDecimal x){return x==null?BigDecimal.ZERO:x;}private static BigDecimal money(BigDecimal x){return z(x).setScale(2,RoundingMode.HALF_UP);}private static BigDecimal q(BigDecimal x){return z(x).setScale(2,RoundingMode.HALF_UP);}
  }
  private record Key(Long id,String name){}
  private static final Comparator<Key> KEY_ORDER=Comparator
      .comparing(Key::id,Comparator.nullsLast(Long::compareTo))
      .thenComparing(Key::name);
  private static final class Group{long count,manager;Map<String,BigDecimal>units=new TreeMap<>();void add(ProjectedRecordRow r){count++;if("FARM_CARE_MANAGER".equals(r.getCreatedRole()))manager++;units.merge(r.getUnit()==null?"미지정":r.getUnit().trim().toLowerCase(Locale.ROOT),r.getQuantity()==null?BigDecimal.ZERO:r.getQuantity(),BigDecimal::add);}}
  private static final class Customer{long count,manager;BigDecimal gross=BigDecimal.ZERO,fee=BigDecimal.ZERO,net=BigDecimal.ZERO;void add(ProjectedRecordRow r){count++;if("FARM_CARE_MANAGER".equals(r.getCreatedRole()))manager++;gross=gross.add(r.getGrossAmount()==null?BigDecimal.ZERO:r.getGrossAmount());fee=fee.add(r.getFeeAmount()==null?BigDecimal.ZERO:r.getFeeAmount());net=net.add(r.getNetAmount()==null?BigDecimal.ZERO:r.getNetAmount());}}
}
