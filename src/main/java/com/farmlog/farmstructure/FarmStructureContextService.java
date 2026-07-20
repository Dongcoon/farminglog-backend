package com.farmlog.farmstructure;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.farmstructure.dto.StructureDtos.*;
import com.farmlog.farmstructure.entity.StructureFarmRow;
import com.farmlog.farmstructure.mapper.FarmStructureMapper;
import com.farmlog.records.dto.PageResponse;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

@Service
public class FarmStructureContextService {
  private final FarmStructureMapper mapper; private final StructureAccessGuard access;
  public FarmStructureContextService(FarmStructureMapper mapper, StructureAccessGuard access) {
    this.mapper=mapper; this.access=access;
  }
  public PageResponse<ContextOrganization> organizations(Long userId, Long organizationId, String q, int page, int size) {
    validPage(page,size); String query=query(q); boolean system=access.system(userId);
    if(system && organizationId==null) throw validation("SYSTEM_ADMIN은 organizationId가 필요합니다.");
    if(system && query!=null) throw validation("SYSTEM_ADMIN 명시 조회에서는 q를 사용할 수 없습니다.");
    var rows=mapper.findContextOrganizations(userId,organizationId,query,system,page*size,size);
    var content=rows.stream().map(r->new ContextOrganization(l(r,"id"),s(r,"name"),s(r,"org_type"),s(r,"status"),s(r,"capability"),n(r,"active_owned_farm_count"))).toList();
    return PageResponse.of(content,page,size,mapper.countContextOrganizations(userId,organizationId,query,system));
  }
  public PageResponse<ContextFarm> farms(Long userId, Long organizationId, String q, int page, int size) {
    validPage(page,size); String query=query(q); boolean admin=access.organizationAdmin(userId,organizationId);
    var rows=mapper.findContextFarms(userId,organizationId,query,admin,page*size,size);
    return PageResponse.of(rows.stream().map(this::farm).toList(),page,size,mapper.countContextFarms(userId,organizationId,query,admin));
  }
  public ContextFarmDetail farm(Long userId, Long organizationId, Long farmId) {
    boolean admin=access.organizationAdmin(userId,organizationId);
    StructureFarmRow f=mapper.findContextFarm(userId,farmId,admin).filter(x->Objects.equals(x.getOrganizationId(),organizationId))
        .orElseThrow(()->new BusinessException(ErrorCode.FARM_NOT_FOUND));
    var owners=mapper.findOwners(farmId).stream().map(r->new Owner(l(r,"id"),s(r,"display_name"),s(r,"role"),s(r,"status"))).toList();
    var zones=mapper.findCurrentZones(farmId).stream().map(r->new Zone(l(r,"id"),s(r,"name"),"Y".equals(s(r,"active_yn")),l(r,"version"),new Assignment(l(r,"assignment_id"),l(r,"assignment_farm_id"),date(r,"effective_from"),l(r,"assignment_version")))).toList();
    Map<String,Object> m=mapper.findMasterSummary(farmId);
    return new ContextFarmDetail(f.getId(),f.getOrganizationId(),f.getFarmCode(),f.getName(),f.getOwnerUserId(),f.getLifecycleStatus(),f.getStatus(),f.getStructureVersion(),f.getCreatedAt(),f.getCurrentZoneCount(),owners,zones,new MasterSummary(n(m,"crop_variety"),n(m,"season"),n(m,"work_type"),n(m,"customer"),n(m,"material")));
  }
  private ContextFarm farm(StructureFarmRow f){return new ContextFarm(f.getId(),f.getOrganizationId(),f.getFarmCode(),f.getName(),f.getOwnerUserId(),f.getLifecycleStatus(),f.getStatus(),f.getStructureVersion(),f.getCreatedAt(),f.getCurrentZoneCount());}
  private void validPage(int p,int s){if(p<0||s<1||s>100)throw validation("page/size 범위를 확인해주세요.");}
  private String query(String q){if(q==null||q.isBlank())return null;String x=q.trim();if(x.length()>100)throw validation("검색어는 100자 이하여야 합니다.");return x.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
  private Object v(Map<String,Object> m,String k){return m.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(k)).map(Map.Entry::getValue).findFirst().orElse(null);}
  private String s(Map<String,Object>m,String k){Object v=v(m,k);return v==null?null:v.toString();}
  private Long l(Map<String,Object>m,String k){Object v=v(m,k);return v==null?null:((Number)v).longValue();}
  private long n(Map<String,Object>m,String k){Long v=l(m,k);return v==null?0:v;}
  private LocalDate date(Map<String,Object>m,String k){Object v=v(m,k);return v instanceof LocalDate d?d:LocalDate.parse(v.toString());}
  private BusinessException validation(String m){return new BusinessException(ErrorCode.VALIDATION_FAILED,m);}
}
