package com.farmlog.organizationdashboard;

import static com.farmlog.organizationdashboard.dto.OrganizationDashboardDtos.*;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.organizationdashboard.entity.*;
import com.farmlog.organizationdashboard.mapper.OrganizationDashboardMapper;
import com.farmlog.records.dto.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationDashboardService {
  public static final String RECORDED_STRUCTURE = "RECORDED_STRUCTURE";
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final Set<String> LIFECYCLES = Set.of("ACTIVE","MERGED","SPLIT","ARCHIVED");
  private static final Set<String> STATUSES = Set.of("ACTIVE","INACTIVE");
  private static final Set<String> DATA_STATUSES = Set.of("ALL","WITH_DATA","NO_DATA");
  private static final Map<String,String> SORTS = Map.of(
      "name,asc","f.name ASC", "name,desc","f.name DESC",
      "createdAt,asc","f.created_at ASC", "createdAt,desc","f.created_at DESC");

  private final OrganizationDashboardMapper mapper;
  private final OrganizationAccessGuard guard;
  private final ObjectMapper json;

  public OrganizationDashboardService(OrganizationDashboardMapper mapper,
      OrganizationAccessGuard guard, ObjectMapper json) {
    this.mapper=mapper; this.guard=guard; this.json=json;
  }

  /** snapshotAt은 ORG_ADMIN 확인 SELECT보다 먼저 잡고 모든 집계와 감사를 단일 RR 트랜잭션에 둔다. */
  @Transactional(isolation=Isolation.REPEATABLE_READ)
  public DashboardResponse dashboard(Long actor,Long organizationId,String monthText,String basis) {
    MonthScope scope=scope(monthText,basis);
    positive(organizationId,"organizationId");
    LocalDateTime snapshotAt=LocalDateTime.now();
    OrganizationAccessRow organization=guard.requireActiveOrgAdmin(actor,organizationId);
    List<Long> farmIds=mapper.findOrganizationFarmIds(organizationId);
    long activeFarmCount=mapper.countActiveFarms(organizationId);
    Map<Long,FarmMetric> metrics=load(farmIds,scope,true);
    FarmMetric total=combine(metrics.values());
    DashboardResponse response=new DashboardResponse(organization(organization),scope.month.toString(),
        scope.start,scope.endExclusive.minusDays(1),RECORDED_STRUCTURE,snapshotAt,farmIds.size(),
        activeFarmCount,metrics.values().stream().filter(FarmMetric::hasData).count(),total.included(),
        total.recordCounts(),total.harvestByUnit(),total.gross(),total.fee(),total.net(),
        total.averagePrices(),total.changes(),total.managerCount());
    audit(organizationId,null,actor,"ORG_DASHBOARD_VIEW","ORGANIZATION",organizationId,
        details("organizationId",organizationId,"month",scope.month.toString(),"resultCount",farmIds.size()));
    return response;
  }

  @Transactional(isolation=Isolation.REPEATABLE_READ)
  public PageResponse<FarmRowResponse> farms(Long actor,Long organizationId,OrganizationFarmFilter raw) {
    ValidFarmFilter filter=filter(raw);
    positive(organizationId,"organizationId");
    LocalDateTime snapshotAt=LocalDateTime.now();
    guard.requireActiveOrgAdmin(actor,organizationId);
    List<OrganizationFarmRow> pageRows=mapper.findFarmPage(organizationId,filter.q,filter.lifecycle,
        filter.status,filter.dataStatus,filter.scope.start,filter.scope.endExclusive,
        filter.orderBy,filter.direction,filter.page*filter.size,filter.size);
    long total=mapper.countFarmPage(organizationId,filter.q,filter.lifecycle,filter.status,
        filter.dataStatus,filter.scope.start,filter.scope.endExclusive);
    List<Long> pageIds=pageRows.stream().map(OrganizationFarmRow::getId).toList();
    Map<Long,FarmMetric> metrics=load(pageIds,filter.scope,false);
    List<FarmRowResponse> content=pageRows.stream().map(row->farmRow(row,metrics.get(row.getId()))).toList();
    PageResponse<FarmRowResponse> response=PageResponse.of(content,filter.page,filter.size,total);
    audit(organizationId,null,actor,"ORG_FARM_LIST_VIEW","ORGANIZATION",organizationId,
        details("organizationId",organizationId,"month",filter.scope.month.toString(),
            "qPresent",filter.q!=null,"lifecycleStatusPresent",filter.lifecycle!=null,
            "statusPresent",filter.status!=null,"dataStatusFilterPresent",!"ALL".equals(filter.dataStatus),
            "page",filter.page,"size",filter.size,"resultCount",content.size()));
    return response;
  }

  @Transactional(isolation=Isolation.REPEATABLE_READ)
  public FarmDetailResponse detail(Long actor,Long organizationId,Long farmId,String monthText,String basis) {
    MonthScope scope=scope(monthText,basis);
    positive(organizationId,"organizationId");positive(farmId,"farmId");
    LocalDateTime snapshotAt=LocalDateTime.now();
    guard.requireActiveOrgAdmin(actor,organizationId);
    OrganizationFarmRow row=mapper.findFarmDetail(organizationId,farmId)
        .orElseThrow(()->new BusinessException(ErrorCode.FARM_NOT_FOUND));
    FarmMetric metric=load(List.of(farmId),scope,true).get(farmId);
    FarmIdentity farm=new FarmIdentity(row.getId(),blank(row.getFarmCode()),row.getName(),
        maskAddress(row.getAddress()),row.getLifecycleStatus(),row.getStatus(),owner(row),crop(row),
        row.getCreatedAt(),row.getUpdatedAt(),row.getActiveMemberCount(),row.getActiveZoneCount());
    DetailMonthly monthly=new DetailMonthly(scope.month.toString(),metric.included(),metric.recordCounts(),
        metric.harvestByUnit(),metric.gross(),metric.fee(),metric.net(),metric.averagePrices(),
        metric.changes(),metric.managerCount());
    FarmDetailResponse response=new FarmDetailResponse(farm,monthly,snapshotAt);
    audit(organizationId,farmId,actor,"ORG_FARM_DETAIL_VIEW","FARM",farmId,
        details("organizationId",organizationId,"farmId",farmId,"month",scope.month.toString(),
            "resultCount",1));
    return response;
  }

  private Map<Long,FarmMetric> load(List<Long> farmIds,MonthScope scope,boolean includeAverages) {
    Map<Long,FarmMetric> result=new LinkedHashMap<>();
    farmIds.forEach(id->result.put(id,new FarmMetric()));
    if(farmIds.isEmpty())return result;
    mapper.findRecordCounts(farmIds,scope.start,scope.endExclusive)
        .forEach(row->result.get(row.getFarmId()).add(row));
    mapper.findHarvestComparison(farmIds,scope.previousStart,scope.start,scope.endExclusive)
        .forEach(row->result.get(row.getFarmId()).add(row));
    mapper.findSalesComparison(farmIds,scope.previousStart,scope.start,scope.endExclusive)
        .forEach(row->result.get(row.getFarmId()).add(row));
    if(includeAverages)mapper.findAverageUnitPrices(farmIds,scope.start,scope.endExclusive)
        .forEach(row->result.get(row.getFarmId()).add(row));
    return result;
  }

  private FarmMetric combine(Collection<FarmMetric> metrics) {
    FarmMetric total=new FarmMetric();
    metrics.forEach(total::add);
    return total;
  }

  private FarmRowResponse farmRow(OrganizationFarmRow row,FarmMetric metric) {
    return new FarmRowResponse(row.getId(),blank(row.getFarmCode()),row.getName(),maskAddress(row.getAddress()),
        row.getLifecycleStatus(),row.getStatus(),owner(row),crop(row),metric.hasData(),metric.included(),
        metric.recordCounts(),metric.harvestByUnit(),metric.gross(),metric.fee(),metric.net(),
        metric.managerCount(),row.getCreatedAt(),row.getUpdatedAt());
  }

  private Owner owner(OrganizationFarmRow row) {
    return new Owner(row.getOwnerUserId(),row.getOwnerDisplayName(),maskEmail(row.getOwnerEmail()));
  }
  private MainCrop crop(OrganizationFarmRow row) {
    return row.getMainCropId()==null?null:new MainCrop(row.getMainCropId(),row.getMainCropName());
  }
  private OrganizationIdentity organization(OrganizationAccessRow row) {
    return new OrganizationIdentity(row.getId(),row.getName(),row.getOrgType(),row.getStatus());
  }

  static String maskEmail(String value) {
    String email=blank(value);if(email==null)return null;int at=email.indexOf('@');
    return at<1?"***":email.substring(0,1)+"***"+email.substring(at);
  }
  static String maskAddress(String value) {
    String address=blank(value);if(address==null)return null;String[] parts=address.split("\\s+");
    return String.join(" ",Arrays.copyOf(parts,Math.min(2,parts.length)))+" ***";
  }
  private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}

  private MonthScope scope(String monthText,String basis) {
    if(!RECORDED_STRUCTURE.equals(basis))throw validation("basis는 RECORDED_STRUCTURE만 지원합니다.");
    try{YearMonth month=YearMonth.parse(monthText);return new MonthScope(month,month.atDay(1),
        month.plusMonths(1).atDay(1),month.minusMonths(1).atDay(1));}
    catch(DateTimeParseException|NullPointerException e){throw validation("month는 YYYY-MM 형식이어야 합니다.");}
  }

  private ValidFarmFilter filter(OrganizationFarmFilter raw) {
    if(raw==null)throw validation("조회 조건이 필요합니다.");MonthScope scope=scope(raw.month(),raw.basis());
    if(raw.page()<0||raw.size()<1||raw.size()>100||(long)raw.page()*raw.size()>Integer.MAX_VALUE)
      throw validation("page는 0 이상, size는 1~100입니다.");
    if(raw.lifecycleStatus()!=null&&!LIFECYCLES.contains(raw.lifecycleStatus()))throw validation("lifecycleStatus가 올바르지 않습니다.");
    if(raw.status()!=null&&!STATUSES.contains(raw.status()))throw validation("status가 올바르지 않습니다.");
    if(raw.dataStatus()==null||!DATA_STATUSES.contains(raw.dataStatus()))throw validation("dataStatus가 올바르지 않습니다.");
    String sort=raw.sort()==null?null:SORTS.get(raw.sort());if(sort==null)throw validation("sort가 올바르지 않습니다.");
    String[] order=sort.split(" ");
    return new ValidFarmFilter(scope,like(raw.q()),raw.lifecycleStatus(),raw.status(),raw.dataStatus(),
        raw.page(),raw.size(),order[0],order[1]);
  }

  private String like(String raw) {
    String value=blank(raw);if(value==null)return null;if(value.length()>100)throw validation("q는 100자 이하입니다.");
    value=value.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
    return "%"+value+"%";
  }

  private void positive(Long value,String name){if(value==null||value<1)throw validation(name+"는 양수여야 합니다.");}

  private void audit(Long organizationId,Long farmId,Long actor,String action,String targetType,
      Long targetId,Map<String,Object> detail) {
    try{mapper.insertAudit(organizationId,farmId,actor,action,targetType,targetId,
        json.writeValueAsString(detail),LocalDateTime.now());}
    catch(JsonProcessingException e){throw new IllegalStateException("조직 조회 감사를 기록하지 못했습니다.",e);}
  }
  private Map<String,Object> details(Object... values){Map<String,Object> out=new LinkedHashMap<>();
    for(int i=0;i<values.length;i+=2)out.put(String.valueOf(values[i]),values[i+1]);return out;}
  private BusinessException validation(String message){return new BusinessException(ErrorCode.VALIDATION_FAILED,message);}

  private final class FarmMetric {
    private final Map<String,OrganizationRecordCountRow> records=new HashMap<>();
    private final Map<String,HarvestTotal> harvests=new TreeMap<>();
    private final Map<String,PriceTotal> prices=new TreeMap<>();
    private OrganizationSalesRow sales=new OrganizationSalesRow();
    void add(OrganizationRecordCountRow row){records.put(row.getRecordType(),row);}
    void add(OrganizationHarvestRow row){harvests.computeIfAbsent(row.getUnit(),ignored->new HarvestTotal()).add(row);}
    void add(OrganizationAverageRow row){prices.computeIfAbsent(row.getUnit(),ignored->new PriceTotal()).add(row);}
    void add(OrganizationSalesRow row){sales=row;}
    void add(FarmMetric other){
      other.records.values().forEach(row->{OrganizationRecordCountRow target=records.computeIfAbsent(row.getRecordType(),ignored->new OrganizationRecordCountRow());target.setRecordType(row.getRecordType());target.setRecordCount(target.getRecordCount()+row.getRecordCount());target.setManagerInputCount(target.getManagerInputCount()+row.getManagerInputCount());});
      other.harvests.forEach((unit,value)->harvests.computeIfAbsent(unit,ignored->new HarvestTotal()).add(value));
      other.prices.forEach((unit,value)->prices.computeIfAbsent(unit,ignored->new PriceTotal()).add(value));
      OrganizationSalesRow merged=new OrganizationSalesRow();merged.setCurrentGross(sum(sales.getCurrentGross(),other.sales.getCurrentGross()));merged.setCurrentFee(sum(sales.getCurrentFee(),other.sales.getCurrentFee()));merged.setCurrentNet(sum(sales.getCurrentNet(),other.sales.getCurrentNet()));merged.setPreviousGross(sum(sales.getPreviousGross(),other.sales.getPreviousGross()));merged.setPreviousFee(sum(sales.getPreviousFee(),other.sales.getPreviousFee()));merged.setPreviousNet(sum(sales.getPreviousNet(),other.sales.getPreviousNet()));merged.setCurrentCount(sales.getCurrentCount()+other.sales.getCurrentCount());merged.setPreviousCount(sales.getPreviousCount()+other.sales.getPreviousCount());sales=merged;
    }
    long count(String type){return records.containsKey(type)?records.get(type).getRecordCount():0;}
    long included(){return count("WORK")+count("PEST_CONTROL")+count("HARVEST")+count("SALES");}
    boolean hasData(){return included()>0;}
    long managerCount(){return records.values().stream().mapToLong(OrganizationRecordCountRow::getManagerInputCount).sum();}
    RecordCounts recordCounts(){return new RecordCounts(count("WORK"),count("PEST_CONTROL"),count("HARVEST"),count("SALES"));}
    List<UnitQuantity> harvestByUnit(){return harvests.entrySet().stream().filter(e->e.getValue().currentCount>0).map(e->new UnitQuantity(e.getKey(),quantity(e.getValue().current))).toList();}
    List<AverageUnitPrice> averagePrices(){return prices.entrySet().stream().map(e->e.getValue().response(e.getKey())).toList();}
    BigDecimal gross(){return money(sales.getCurrentGross());}BigDecimal fee(){return money(sales.getCurrentFee());}BigDecimal net(){return money(sales.getCurrentNet());}
    Changes changes(){List<HarvestChange> hs=harvests.entrySet().stream().map(e->new HarvestChange(e.getKey(),quantity(e.getValue().current),change(e.getValue().current,e.getValue().previous,e.getValue().currentCount,e.getValue().previousCount))).toList();return new Changes(hs,change(sales.getCurrentGross(),sales.getPreviousGross(),sales.getCurrentCount(),sales.getPreviousCount()),change(sales.getCurrentNet(),sales.getPreviousNet(),sales.getCurrentCount(),sales.getPreviousCount()));}
  }
  private static final class HarvestTotal {BigDecimal current=BigDecimal.ZERO,previous=BigDecimal.ZERO;long currentCount,previousCount;void add(OrganizationHarvestRow r){current=current.add(zero(r.getCurrentQuantity()));previous=previous.add(zero(r.getPreviousQuantity()));currentCount+=r.getCurrentCount();previousCount+=r.getPreviousCount();}void add(HarvestTotal r){current=current.add(r.current);previous=previous.add(r.previous);currentCount+=r.currentCount;previousCount+=r.previousCount;}}
  private final class PriceTotal {BigDecimal quantity=BigDecimal.ZERO,gross=BigDecimal.ZERO;void add(OrganizationAverageRow r){quantity=quantity.add(zero(r.getSoldQuantity()));gross=gross.add(zero(r.getGrossSalesAmount()));}void add(PriceTotal r){quantity=quantity.add(r.quantity);gross=gross.add(r.gross);}AverageUnitPrice response(String unit){return new AverageUnitPrice(unit,quantity(quantity),money(gross),money(gross).divide(quantity(quantity),2,RoundingMode.HALF_UP));}}
  private Change change(BigDecimal currentRaw,BigDecimal previousRaw,long currentCount,long previousCount){BigDecimal current=money(currentRaw),previous=money(previousRaw),absolute=money(current.subtract(previous)),rate=null;ChangeStatus status;if(currentCount==0&&previousCount==0)status=ChangeStatus.NO_DATA;else if(current.signum()==0&&previous.signum()==0)status=ChangeStatus.SAME;else if(previous.signum()==0&&current.signum()>0)status=ChangeStatus.NEW;else{int c=current.compareTo(previous);status=c>0?ChangeStatus.UP:c<0?ChangeStatus.DOWN:ChangeStatus.SAME;if(previous.signum()!=0)rate=absolute.multiply(HUNDRED).divide(previous,2,RoundingMode.HALF_UP);}return new Change(current,previous,absolute,rate,status);}
  private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
  private static BigDecimal sum(BigDecimal left,BigDecimal right){return zero(left).add(zero(right));}
  private static BigDecimal money(BigDecimal value){return zero(value).setScale(2,RoundingMode.HALF_UP);}
  private static BigDecimal quantity(BigDecimal value){return zero(value).setScale(2,RoundingMode.HALF_UP);}
  private record MonthScope(YearMonth month,java.time.LocalDate start,java.time.LocalDate endExclusive,java.time.LocalDate previousStart){}
  private record ValidFarmFilter(MonthScope scope,String q,String lifecycle,String status,String dataStatus,int page,int size,String orderBy,String direction){}
}
