package com.farmlog.farmstructure;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.farm.entity.*;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farm.mapper.FarmZoneMapper;
import com.farmlog.farmstructure.dto.StructureDtos.*;
import com.farmlog.farmstructure.entity.*;
import com.farmlog.farmstructure.mapper.FarmStructureMapper;
import com.farmlog.records.dto.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.*;
import java.util.*;

@Service
public class FarmStructureService {
  private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
  private static final Set<String> POLICIES=Set.of("RECORDED_ONLY","FROM_EFFECTIVE_DATE","SELECTED_PERIOD","ALL_HISTORY");
  private static final Set<String> SCOPES=Set.of("CROP_VARIETY","SEASON","WORK_TYPE","CUSTOMER","MATERIAL");
  private final FarmStructureMapper mapper; private final FarmMapper farms; private final FarmZoneMapper zones;
  private final StructureAccessGuard access; private final StructureCanonicalJson json;
  public FarmStructureService(FarmStructureMapper mapper,FarmMapper farms,FarmZoneMapper zones,
      StructureAccessGuard access,StructureCanonicalJson json){this.mapper=mapper;this.farms=farms;this.zones=zones;this.access=access;this.json=json;}

  @Transactional(isolation=Isolation.REPEATABLE_READ)
  public PreviewResponse previewMerge(Long userId,MergePreviewRequest raw){
    MergePreviewRequest req=normalize(raw); String requestHash=json.hash(req);
    Optional<StructureEventRow> old=mapper.findByPreviewRequest(userId,req.clientRequestId());
    if(old.isPresent()) return existingPreview(old.get(),requestHash);
    Preview p=calculateMerge(userId,req);
    return savePreview(userId,"MERGE",req.clientRequestId(),req.effectiveDate(),req.reportPolicy(),req.selectedPeriod(),req.reason(),req,requestHash,p);
  }

  @Transactional(isolation=Isolation.REPEATABLE_READ)
  public PreviewResponse previewSplit(Long userId,SplitPreviewRequest raw){
    SplitPreviewRequest req=normalize(raw); String requestHash=json.hash(req);
    Optional<StructureEventRow> old=mapper.findByPreviewRequest(userId,req.clientRequestId());
    if(old.isPresent()) return existingPreview(old.get(),requestHash);
    Preview p=calculateSplit(userId,req);
    return savePreview(userId,"SPLIT",req.clientRequestId(),req.effectiveDate(),req.reportPolicy(),req.selectedPeriod(),req.reason(),req,requestHash,p);
  }

  @Transactional(isolation=Isolation.READ_COMMITTED)
  public EventDetail confirm(Long userId,String type,ConfirmRequest req){
    uuid(req==null?null:req.clientRequestId());
    String confirmHash=json.hash(req);
    Optional<StructureEventRow> retry=mapper.findByConfirmRequest(userId,req.clientRequestId());
    if(retry.isPresent()){
      if(!Objects.equals(retry.get().getConfirmRequestHash(),confirmHash))throw new BusinessException(ErrorCode.STRUCTURE_IDEMPOTENCY_CONFLICT);
      return detailAuthorized(userId,retry.get());
    }
    StructureEventRow draft=mapper.findEvent(req.previewEventId()).orElseThrow(()->new BusinessException(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND));
    if(!type.equals(draft.getEventType()))throw validation("preview 유형과 confirm 경로가 다릅니다.");
    if(!"DRAFT".equals(draft.getStatus()))throw new BusinessException(ErrorCode.EVENT_ALREADY_CONFIRMED);
    if(draft.getPreviewExpiresAt().compareTo(LocalDateTime.now())<=0)throw new BusinessException(ErrorCode.PREVIEW_EXPIRED);
    if(!Objects.equals(req.previewVersion(),draft.getVersion())||!Objects.equals(req.previewHash(),draft.getPreviewHash()))throw new BusinessException(ErrorCode.PREVIEW_STALE);

    Object request="MERGE".equals(type)?json.read(draft.getRequestSnapshotJson(),MergePreviewRequest.class):json.read(draft.getRequestSnapshotJson(),SplitPreviewRequest.class);
    List<Long> sourceIds="MERGE".equals(type)?((MergePreviewRequest)request).sourceFarmIds():List.of(((SplitPreviewRequest)request).sourceFarmId());
    Target target="MERGE".equals(type)?((MergePreviewRequest)request).target():((SplitPreviewRequest)request).target();
    List<Long> related=new ArrayList<>(sourceIds); if("EXISTING".equals(target.mode()))related.add(target.farmId()); related=related.stream().distinct().sorted().toList();
    mapper.lockOrganization(draft.getOrganizationId()).orElseThrow(()->new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
    List<StructureFarmRow> locked=mapper.lockFarms(related);
    if(locked.size()!=related.size()||locked.stream().anyMatch(f->!"ACTIVE".equals(f.getStatus())||!"ACTIVE".equals(f.getLifecycleStatus())))throw new BusinessException(ErrorCode.PREVIEW_STALE);
    access.requireAll(userId,draft.getOrganizationId(),related);

    List<Long> zoneIds="MERGE".equals(type)?mapper.findCurrentZoneIds(sourceIds.stream().filter(id->!Objects.equals(id,target.farmId())).toList()):((SplitPreviewRequest)request).zoneIds();
    List<Map<String,Object>> lockedZones=zoneIds.isEmpty()?List.of():mapper.lockCurrentZones(zoneIds);
    if(lockedZones.size()!=zoneIds.size())throw new BusinessException(ErrorCode.PREVIEW_STALE);
    StructureEventRow current=mapper.lockEvent(draft.getId()).orElseThrow(()->new BusinessException(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND));
    if (!"DRAFT".equals(current.getStatus())) {
      // 최초 lookup을 동시에 통과한 동일 confirm UUID는 event lock 뒤 current-read로 수렴한다.
      if (Objects.equals(current.getConfirmRequestId(), req.clientRequestId())
          && Objects.equals(current.getConfirmRequestHash(), confirmHash)) {
        return detailAuthorized(userId, current);
      }
      throw new BusinessException(ErrorCode.EVENT_ALREADY_CONFIRMED);
    }
    if (!Objects.equals(current.getVersion(), req.previewVersion())) {
      throw new BusinessException(ErrorCode.PREVIEW_STALE);
    }
    Preview recomputed="MERGE".equals(type)?calculateMerge(userId,(MergePreviewRequest)request):calculateSplit(userId,(SplitPreviewRequest)request);
    if(!Objects.equals(hashPreview(request,recomputed),draft.getPreviewHash()))throw new BusinessException(ErrorCode.PREVIEW_STALE);
    if(recomputed.conflicts.stream().anyMatch(Conflict::resolutionRequired))throw validation("모든 기준정보 충돌을 해결한 뒤 다시 확인해주세요.");
    try {
      if (mapper.claimConfirmRequest(draft.getId(), draft.getVersion(), req.clientRequestId(),
          confirmHash, userId) != 1) throw new BusinessException(ErrorCode.PREVIEW_STALE);
    } catch (DataIntegrityViolationException duplicateRequestId) {
      StructureEventRow winner = mapper.findByConfirmRequest(userId, req.clientRequestId())
          .orElseThrow(() -> duplicateRequestId);
      if (Objects.equals(winner.getConfirmRequestHash(), confirmHash)) return detailAuthorized(userId, winner);
      throw new BusinessException(ErrorCode.STRUCTURE_IDEMPOTENCY_CONFLICT);
    }

    LocalDateTime now=LocalDateTime.now(); Long targetId=target.farmId(); boolean newTarget="NEW".equals(target.mode());
    if(newTarget){
      FarmEntity f=FarmEntity.builder().organizationId(draft.getOrganizationId()).name(target.name()).ownerUserId(target.ownerUserId()).lifecycleStatus("ACTIVE").status("ACTIVE").structureVersion(0L).createdBy(userId).createdAt(now).build();
      farms.insert(f); targetId=f.getId(); mapper.copyMembersToNewTarget(sourceIds,targetId,target.ownerUserId(),now);
    }
    List<String> selectedScopes="MERGE".equals(type)?((MergePreviewRequest)request).masterDataScopes():((SplitPreviewRequest)request).masterDataScopes();
    List<Resolution> selectedResolutions="MERGE".equals(type)?((MergePreviewRequest)request).masterDataResolutions():((SplitPreviewRequest)request).masterDataResolutions();
    insertTargetItem(draft.getId(),targetId,newTarget,target,now);
    applyMasterPlan(draft.getId(),sourceIds,targetId,draft.getOrganizationId(),selectedScopes,selectedResolutions,userId,now);
    if(newTarget)recordMemberItems(draft.getId(),targetId,now);
    for(StructureFarmRow f:locked){ if(sourceIds.contains(f.getId())) insertFarmItem(draft.getId(),f,targetId,type,now); }
    for(Map<String,Object> z:lockedZones) moveZone(draft,userId,targetId,z,now);
    for(Long sourceId:sourceIds){
      if(Objects.equals(sourceId,targetId))continue;
      String lifecycle="MERGE".equals(type)?"MERGED":(mapper.countCurrentZones(sourceId)==0?"SPLIT":"ACTIVE");
      mapper.updateLifecycle(sourceId,lifecycle,userId,now);
    }
    List<Long> affected=new ArrayList<>(related);if(newTarget)affected.add(targetId);affected=affected.stream().distinct().sorted().toList();
    for(Long id:affected)farms.incrementStructureVersion(id,userId,now);
    String stateHash=fingerprint(affected,newTarget,targetId);
    if(mapper.confirmEvent(draft.getId(),draft.getVersion(),req.clientRequestId(),confirmHash,stateHash,userId,now)!=1)throw new BusinessException(ErrorCode.PREVIEW_STALE);
    mapper.insertAudit(draft.getOrganizationId(),targetId,userId,"FARM_STRUCTURE_"+type+"_CONFIRMED",draft.getId(),json.write(Map.of("reportPolicy",draft.getReportPolicy(),"sourceFarmCount",sourceIds.size())),now);
    return detailAuthorized(userId,mapper.findEvent(draft.getId()).orElseThrow());
  }

  @Transactional(isolation=Isolation.READ_COMMITTED)
  public EventDetail cancel(Long userId,Long eventId,CancelRequest req){
    uuid(req==null?null:req.clientRequestId()); String hash=json.hash(Map.of("eventId",eventId,"request",req));
    Optional<StructureEventRow> retry=mapper.findByPreviewRequest(userId,req.clientRequestId());
    if(retry.isPresent()){
      if(!"CANCEL".equals(retry.get().getEventType())||!Objects.equals(retry.get().getRequestHash(),hash))throw new BusinessException(ErrorCode.STRUCTURE_IDEMPOTENCY_CONFLICT);
      return detailAuthorized(userId,retry.get());
    }
    StructureEventRow original=mapper.findEvent(eventId).orElseThrow(()->new BusinessException(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND));
    List<StructureItemRow> items=mapper.findItems(eventId); List<Long> related=items.stream().flatMap(i->java.util.stream.Stream.of(i.getSourceFarmId(),i.getTargetFarmId())).filter(Objects::nonNull).distinct().sorted().toList();
    try{access.requireAll(userId,original.getOrganizationId(),related);}catch(BusinessException denied){throw new BusinessException(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND);}
    if(!"CONFIRMED".equals(original.getStatus())||original.getReversedByEventId()!=null||!Objects.equals(original.getVersion(),req.version()))throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);
    mapper.lockOrganization(original.getOrganizationId()).orElseThrow(()->new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
    mapper.lockFarms(related); access.requireAll(userId,original.getOrganizationId(),related);
    List<Long> zoneIds=items.stream().map(StructureItemRow::getZoneId).filter(Objects::nonNull).distinct().sorted().toList();
    List<Map<String,Object>> currentZones=zoneIds.isEmpty()?List.of():mapper.lockCurrentZones(zoneIds);
    StructureEventRow locked=mapper.lockEvent(eventId).orElseThrow();
    if ("CANCELLED".equals(locked.getStatus()) && locked.getReversedByEventId() != null) {
      StructureEventRow concurrent = mapper.findEvent(locked.getReversedByEventId()).orElseThrow();
      if (Objects.equals(concurrent.getClientRequestId(), req.clientRequestId())
          && Objects.equals(concurrent.getRequestHash(), hash)) {
        return detailAuthorized(userId, concurrent);
      }
      throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);
    }
    if (!"CONFIRMED".equals(locked.getStatus()) || locked.getReversedByEventId() != null
        || !Objects.equals(locked.getVersion(), req.version())) {
      throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);
    }
    // 원 이벤트 이후 같은 농장/구역을 사용한 event chain은 역적용 순서가
    // 애매하므로 취소하지 않는다.
    if (mapper.countNewerRelatedEvents(eventId, locked.getConfirmedAt(), related, zoneIds) > 0) {
      throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);
    }
    if(!Objects.equals(locked.getConfirmedStateHash(),fingerprint(related,isNew(items),targetId(items))))throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);
    if(isNew(items)&&mapper.countBlockingDependents(targetId(items))>0)throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);
    LocalDateTime now=LocalDateTime.now(); LocalDate cancelDate=LocalDate.now(SEOUL);
    StructureEventRow cancel = new StructureEventRow();
    cancel.setOrganizationId(original.getOrganizationId());
    cancel.setEventType("CANCEL");
    cancel.setEventName("CANCEL-" + req.clientRequestId());
    cancel.setEffectiveDate(cancelDate);
    cancel.setStatus("CONFIRMED");
    cancel.setVersion(0L);
    cancel.setClientRequestId(req.clientRequestId());
    cancel.setRequestHash(hash);
    // CANCEL detail은 원 이벤트의 관계·영향을 그대로 보여주고 reason은 전용 컬럼에만 보존한다.
    cancel.setRequestSnapshotJson(original.getRequestSnapshotJson());
    cancel.setImpactJson(original.getImpactJson());
    cancel.setConflictsJson(original.getConflictsJson());
    cancel.setWarningsJson(original.getWarningsJson());
    cancel.setReportPolicy(original.getReportPolicy());
    cancel.setPeriodStart(original.getPeriodStart());
    cancel.setPeriodEndExclusive(original.getPeriodEndExclusive());
    cancel.setReversesEventId(eventId);
    cancel.setReason(req.reason());
    cancel.setCreatedBy(userId);
    cancel.setCreatedAt(now);
    cancel.setConfirmedBy(userId);
    cancel.setConfirmedAt(now);
    try {
      mapper.insertEvent(cancel);
    } catch (DataIntegrityViolationException duplicateRequestId) {
      StructureEventRow winner = mapper.findByPreviewRequest(userId, req.clientRequestId())
          .orElseThrow(() -> duplicateRequestId);
      if ("CANCEL".equals(winner.getEventType()) && Objects.equals(winner.getReversesEventId(), eventId)
          && Objects.equals(winner.getRequestHash(), hash)) return detailAuthorized(userId, winner);
      throw new BusinessException(ErrorCode.STRUCTURE_IDEMPOTENCY_CONFLICT);
    }
    recordCancelRelations(cancel.getId(), items, now);
    Map<Long,Map<String,Object>> byZone=new HashMap<>();for(Map<String,Object> z:currentZones)byZone.put(l(z,"zone_id"),z);
    for(StructureItemRow item:items){if(item.getZoneId()==null||!"ZONE".equals(item.getItemType()))continue;Map<String,Object> z=byZone.get(item.getZoneId());if(z==null)throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);Long source=item.getSourceFarmId();Long target=item.getTargetFarmId();if(mapper.moveZone(item.getZoneId(),target,source,l(z,"zone_version"),userId,now)!=1)throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);Long aid=l(z,"assignment_id"),av=l(z,"assignment_version");LocalDate from=date(z,"effective_from");if(cancelDate.equals(from)){if(mapper.moveAssignmentInPlace(aid,av,source,cancel.getId(),userId,now)!=1)throw new BusinessException(ErrorCode.PERIOD_CONFLICT);}else{if(mapper.closeAssignment(aid,av,cancelDate,cancel.getId(),userId,now)!=1)throw new BusinessException(ErrorCode.PERIOD_CONFLICT);zones.insertAssignment(FarmZoneAssignmentEntity.builder().organizationId(original.getOrganizationId()).zoneId(item.getZoneId()).farmId(source).effectiveFrom(cancelDate).changeEventId(cancel.getId()).activeYn("Y").version(0L).createdBy(userId).createdAt(now).build());}StructureItemRow inverse=new StructureItemRow();inverse.setEventId(cancel.getId());inverse.setItemType("ZONE");inverse.setSourceFarmId(target);inverse.setTargetFarmId(source);inverse.setZoneId(item.getZoneId());inverse.setAction("INVERSE");inverse.setPeriodStart(cancelDate);inverse.setBeforeJson(item.getAfterJson());inverse.setAfterJson(item.getBeforeJson());inverse.setCreatedAt(now);mapper.insertItem(inverse);}
    for(StructureItemRow item:items){if("FARM".equals(item.getItemType())&&item.getSourceFarmId()!=null)mapper.updateLifecycle(item.getSourceFarmId(),"ACTIVE",userId,now);}
    if(isNew(items)){
      Long createdTargetId=targetId(items);
      // dependent row가 없는 신규 target의 확정 시 복사 행을 먼저 역적용한다.
      mapper.deactivateTargetMembers(createdTargetId,now);
      mapper.deactivateTargetCrops(createdTargetId,userId,now);
      mapper.deactivateTargetVarieties(createdTargetId,userId,now);
      mapper.deactivateTargetSeasons(createdTargetId,userId,now);
      mapper.deactivateTargetWorkTypes(createdTargetId,userId,now);
      mapper.deactivateTargetCustomers(createdTargetId,userId,now);
      mapper.deactivateTargetMaterials(createdTargetId,userId,now);
      mapper.updateLifecycle(createdTargetId,"ARCHIVED",userId,now);
    }else{
      // EXISTING target에서는 이 event가 생성/재활성화한 기준정보만 이전 상태로 되돌린다.
      items.stream().filter(item->"MASTER".equals(item.getItemType()))
          .filter(item->masterChanged(item.getAfterJson())).forEach(item->mapper.deactivateMasterResource(
              item.getResourceType(),masterName(item.getAfterJson()),item.getTargetFarmId(),
              item.getTargetResourceId(),userId,now));
    }
    for(Long id:related)farms.incrementStructureVersion(id,userId,now);
    if(mapper.cancelOriginal(eventId,req.version(),cancel.getId(),userId,now)!=1)throw new BusinessException(ErrorCode.STRUCTURE_CANCEL_BLOCKED);
    mapper.insertAudit(original.getOrganizationId(),targetId(items),userId,"FARM_STRUCTURE_CANCELLED",cancel.getId(),json.write(Map.of("reversesEventId",eventId)),now);
    return detailAuthorized(userId,mapper.findEvent(cancel.getId()).orElseThrow());
  }

  public EventDetail detail(Long userId,Long eventId){return detailAuthorized(userId,mapper.findEvent(eventId).orElseThrow(()->new BusinessException(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND)));}

  @Transactional(readOnly=true)
  public PageResponse<EventSummary> events(Long userId,Long organizationId,String eventType,String status,
      Long sourceFarmId,Long targetFarmId,LocalDate effectiveFrom,LocalDate effectiveToExclusive,
      int page,int size,String sort){
    if(organizationId==null||page<0||size<1||size>100)throw validation("organizationId/page/size를 확인해주세요.");
    if(eventType!=null&&!Set.of("MERGE","SPLIT","CANCEL").contains(eventType))throw validation("eventType이 올바르지 않습니다.");
    if(status!=null&&!Set.of("DRAFT","CONFIRMED","CANCELLED","EXPIRED").contains(status))throw validation("status가 올바르지 않습니다.");
    if(effectiveFrom!=null&&effectiveToExclusive!=null&&!effectiveFrom.isBefore(effectiveToExclusive))throw validation("조회 기간을 확인해주세요.");
    if(sort!=null&&!"effectiveDate,desc".equals(sort))throw validation("sort는 effectiveDate,desc만 지원합니다.");
    boolean admin=access.organizationAdmin(userId,organizationId);LocalDateTime now=LocalDateTime.now();
    List<EventSummary> content=mapper.findEvents(userId,organizationId,admin,eventType,status,sourceFarmId,targetFarmId,effectiveFrom,effectiveToExclusive,now,page*size,size).stream().map(e->summary(detailAuthorized(userId,e))).toList();
    return PageResponse.of(content,page,size,mapper.countEvents(userId,organizationId,admin,eventType,status,sourceFarmId,targetFarmId,effectiveFrom,effectiveToExclusive,now));
  }

  private Preview calculateMerge(Long userId,MergePreviewRequest req){
    List<StructureFarmRow> fs=mapper.findFarmsByIds(related(req.sourceFarmIds(),req.target()));if(fs.size()!=related(req.sourceFarmIds(),req.target()).size())throw validation("농장을 찾을 수 없습니다.");validateFarms(userId,fs);if(fs.stream().anyMatch(f->req.effectiveDate().isBefore(f.getCreatedAt().toLocalDate())))throw validation("기준일은 관련 농장 생성일보다 빠를 수 없습니다.");if("NEW".equals(req.target().mode())&&!activeSourceOwner(req.sourceFarmIds(),req.target().ownerUserId()))throw validation("새 농장주는 source의 ACTIVE 농장주여야 합니다.");List<Long> moveSources=req.sourceFarmIds().stream().filter(id->!Objects.equals(id,req.target().farmId())).toList();List<Long> zoneIds=moveSources.isEmpty()?List.of():mapper.findCurrentZoneIds(moveSources);if(!zoneIds.isEmpty()&&mapper.findCurrentZonesByIds(zoneIds).stream().anyMatch(z->req.effectiveDate().isBefore(date(z,"effective_from"))))throw validation("기준일은 현재 구역 소속 시작일보다 빠를 수 없습니다.");return impact(req.sourceFarmIds(),req.target(),zoneIds,req.masterDataScopes(),req.masterDataResolutions(),false);
  }
  private Preview calculateSplit(Long userId,SplitPreviewRequest req){
    List<Long> ids=related(List.of(req.sourceFarmId()),req.target());List<StructureFarmRow> fs=mapper.findFarmsByIds(ids);if(fs.size()!=ids.size())throw validation("농장을 찾을 수 없습니다.");validateFarms(userId,fs);if(fs.stream().anyMatch(f->req.effectiveDate().isBefore(f.getCreatedAt().toLocalDate())))throw validation("기준일은 관련 농장 생성일보다 빠를 수 없습니다.");List<Map<String,Object>> zs=mapper.findCurrentZonesByIds(req.zoneIds());if(zs.size()!=req.zoneIds().size()||zs.stream().anyMatch(z->!Objects.equals(l(z,"farm_id"),req.sourceFarmId())||!"Y".equals(s(z,"active_yn"))))throw validation("현재 source 농장의 ACTIVE 구역만 선택할 수 있습니다.");for(Map<String,Object>z:zs)if(req.effectiveDate().isBefore(date(z,"effective_from")))throw validation("기준일은 현재 구역 소속 시작일보다 빠를 수 없습니다.");return impact(List.of(req.sourceFarmId()),req.target(),req.zoneIds(),req.masterDataScopes(),req.masterDataResolutions(),true);
  }
  private Preview impact(List<Long> sourceIds,Target target,List<Long> zoneIds,List<String> scopes,List<Resolution> resolutions,boolean split){Map<String,Object> c=mapper.countImpact(sourceIds);long members=mapper.countActiveMembers(sourceIds);long excluded="EXISTING".equals(target.mode())?mapper.countActiveMembersNotInTarget(sourceIds,target.farmId()):0;MasterCounts masters=masterCounts(sourceIds,scopes);List<Warning>w=new ArrayList<>();if(scopes.contains("SEASON"))w.add(new Warning("SEASON_DEPENDENCY_INCLUDED","작기 이관을 위해 작물·품종 연결을 함께 포함합니다.",Map.of("scope","CROP_VARIETY")));if(excluded>0)w.add(new Warning("EXISTING_TARGET_MEMBERS_EXCLUDED","기존 target에 없는 source 멤버는 자동 복사하지 않습니다.",Map.of("count",excluded,"userIds",mapper.findActiveMemberIdsNotInTarget(sourceIds,target.farmId()))));long sales=split?mapper.countZoneLessSales(sourceIds.get(0)):0;if(split)w.add(new Warning("ZONE_LESS_SALES_REMAIN","구역 정보가 없는 판매 기록은 원본 농장에 남습니다.",Map.of("count",sales)));List<Conflict> conflicts=masterConflicts(sourceIds,target,scopes,resolutions);return new Preview(new Impact(sourceIds.size(),target.mode(),zoneIds.size(),new RecordCounts(n(c,"work"),n(c,"pest_control"),n(c,"harvest"),n(c,"sales"),n(c,"fertilizer")),masters,"NEW".equals(target.mode())?members:0,excluded,sales),conflicts,List.copyOf(w),related(sourceIds,target),zoneIds);}
  private List<Conflict> masterConflicts(
      List<Long> sources, Target target, List<String> scopes, List<Resolution> resolutions) {
    List<Map<String, Object>> rows = mapper.findMasterResources(related(sources, target), scopes);
    Map<String, List<Map<String, Object>>> groups = new TreeMap<>();
    for (Map<String, Object> row : rows) {
      String key = s(row, "resource_type") + ":" + s(row, "name").trim().toLowerCase(Locale.ROOT);
      groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
    }
    Map<String, Resolution> supplied = new HashMap<>();
    for (Resolution resolution : resolutions) {
      if (resolution == null || resolution.conflictId() == null
          || supplied.put(resolution.conflictId(), resolution) != null) {
        throw validation("충돌 resolution ID를 확인해주세요.");
      }
    }
    Set<String> plannedDestinations = new HashSet<>();
    if ("EXISTING".equals(target.mode())) {
      rows.stream().filter(row -> Objects.equals(l(row, "farm_id"), target.farmId()))
          .map(this::masterKey).forEach(plannedDestinations::add);
    }
    List<Conflict> result = new ArrayList<>();
    for (var entry : groups.entrySet()) {
      List<Map<String, Object>> sourceRows = entry.getValue().stream()
          .filter(row -> sources.contains(l(row, "farm_id")))
          .filter(row -> !"EXISTING".equals(target.mode())
              || !Objects.equals(l(row, "farm_id"), target.farmId()))
          .filter(row -> "Y".equals(s(row, "active")))
          .toList();
      List<Map<String, Object>> targetRows = "EXISTING".equals(target.mode())
          ? entry.getValue().stream().filter(row -> Objects.equals(l(row, "farm_id"), target.farmId())).toList()
          : List.of();
      boolean conflict = !sourceRows.isEmpty()
          && (!targetRows.isEmpty()
              || ("NEW".equals(target.mode())
                  && sourceRows.stream().map(row -> l(row, "resource_id")).distinct().count() > 1));
      if (!conflict) {
        if (!sourceRows.isEmpty() && !plannedDestinations.add(entry.getKey()))
          throw validation("복사 결과 기준정보 이름이 중복됩니다.");
        continue;
      }
      String conflictId = json.hash(Map.of(
          "key", entry.getKey(),
          "sources", sourceRows.stream().map(row -> List.of(l(row, "farm_id"), l(row, "resource_id"))).toList()))
          .substring(0, 24);
      List<String> allowed = "EXISTING".equals(target.mode())
          ? List.of("REUSE_TARGET", "COPY_RENAMED", "SKIP")
          : List.of("REUSE_CANONICAL_SOURCE", "COPY_RENAMED", "SKIP");
      Resolution resolution = supplied.remove(conflictId);
      if (resolution != null) {
        validateResolution(resolution, allowed, sourceRows, targetRows);
        if ("COPY_RENAMED".equals(resolution.action())) {
          String renamed = renamedMasterName(s(sourceRows.get(0), "name"), resolution.newName().trim());
          String destinationKey = resolution.resourceType() + ":"
              + renamed.trim().toLowerCase(Locale.ROOT);
          if (!plannedDestinations.add(destinationKey))
            throw validation("COPY_RENAMED 이름이 다른 기준정보 결과와 중복됩니다.");
        } else if ("REUSE_CANONICAL_SOURCE".equals(resolution.action())
            && !plannedDestinations.add(entry.getKey())) {
          throw validation("기준 source 복사 결과 이름이 중복됩니다.");
        }
      }
      TargetCandidate candidate = targetRows.isEmpty() ? null
          : new TargetCandidate(l(targetRows.get(0), "resource_id"), s(targetRows.get(0), "name"));
      result.add(new Conflict(
          conflictId,
          s(sourceRows.get(0), "resource_type"),
          entry.getKey().substring(entry.getKey().indexOf(':') + 1),
          sourceRows.stream().map(row -> new ConflictSource(
              l(row, "farm_id"), l(row, "resource_id"), s(row, "name"))).toList(),
          candidate,
          allowed,
          resolution == null));
    }
    if (!supplied.isEmpty()) throw validation("현재 preview에 없는 충돌 resolution입니다.");
    return List.copyOf(result);
  }

  private void validateResolution(
      Resolution resolution, List<String> allowed, List<Map<String, Object>> sources,
      List<Map<String, Object>> targets) {
    if (resolution.action() == null || resolution.resourceType() == null
        || !allowed.contains(resolution.action())
        || !Objects.equals(resolution.resourceType(), s(sources.get(0), "resource_type"))) {
      throw validation("허용되지 않는 충돌 처리입니다.");
    }
    boolean valid = switch (resolution.action()) {
      case "REUSE_TARGET" -> resolution.targetResourceId() != null
          && targets.stream().anyMatch(row -> Objects.equals(l(row, "resource_id"), resolution.targetResourceId()))
          && resolution.canonicalSourceResourceId() == null && resolution.newName() == null;
      case "REUSE_CANONICAL_SOURCE" -> resolution.canonicalSourceResourceId() != null
          && sources.stream().anyMatch(row -> Objects.equals(l(row, "resource_id"), resolution.canonicalSourceResourceId()))
          && resolution.targetResourceId() == null && resolution.newName() == null;
      case "COPY_RENAMED" -> resolution.newName() != null && !resolution.newName().trim().isEmpty()
          && resolution.newName().trim().length() <= 100 && resolution.targetResourceId() == null
          && resolution.canonicalSourceResourceId() == null;
      case "SKIP" -> resolution.targetResourceId() == null
          && resolution.canonicalSourceResourceId() == null && resolution.newName() == null;
      default -> false;
    };
    if (!valid) throw validation("충돌 처리 필드 조합이 올바르지 않습니다.");
  }
  private MasterCounts masterCounts(List<Long> ids,List<String> scopes){long cv=0,se=0,w=0,c=0,m=0;for(Long id:ids){Map<String,Object>x=mapper.findMasterSummary(id);if(scopes.contains("CROP_VARIETY")){cv+=n(x,"crop_variety");}if(scopes.contains("SEASON"))se+=n(x,"season");if(scopes.contains("WORK_TYPE"))w+=n(x,"work_type");if(scopes.contains("CUSTOMER"))c+=n(x,"customer");if(scopes.contains("MATERIAL"))m+=n(x,"material");}return new MasterCounts(cv,se,w,c,m);}
  private PreviewResponse savePreview(
      Long userId, String type, String requestId, LocalDate effective, String policy,
      SelectedPeriod period, String reason, Object request, String requestHash, Preview preview) {
    List<StructureFarmRow> farmRows = mapper.findFarmsByIds(preview.relatedFarmIds);
    Long organizationId = farmRows.get(0).getOrganizationId();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expiresAt = now.plusHours(24);
    String previewHash = hashPreview(request, preview);
    StructureEventRow event = new StructureEventRow();
    event.setOrganizationId(organizationId);
    event.setEventType(type);
    event.setEventName(type + "-" + requestId);
    event.setEffectiveDate(effective);
    event.setStatus("DRAFT");
    event.setVersion(0L);
    event.setClientRequestId(requestId);
    event.setRequestHash(requestHash);
    event.setPreviewHash(previewHash);
    event.setPreviewExpiresAt(expiresAt);
    event.setRequestSnapshotJson(json.write(request));
    event.setImpactJson(json.write(preview.impact));
    event.setConflictsJson(json.write(preview.conflicts));
    event.setWarningsJson(json.write(preview.warnings));
    event.setReportPolicy(policy);
    if (period != null) {
      event.setPeriodStart(period.from());
      event.setPeriodEndExclusive(period.toExclusive());
    }
    event.setReason(reason);
    event.setCreatedBy(userId);
    event.setCreatedAt(now);
    try {
      // actor+UUID unique 충돌은 동일 요청의 동시 전송일 수 있으므로 current-read로 복구한다.
      mapper.insertEvent(event);
    } catch (DataIntegrityViolationException duplicate) {
      StructureEventRow existing = mapper.findByPreviewRequest(userId, requestId).orElseThrow(() -> duplicate);
      return existingPreview(existing, requestHash);
    }
    recordDraftRelations(event.getId(), request, now);
    mapper.insertAudit(
        organizationId, preview.relatedFarmIds.get(0), userId,
        "FARM_STRUCTURE_" + type + "_PREVIEW", event.getId(),
        json.write(Map.of(
            "sourceFarmCount", preview.impact.sourceFarmCount(),
            "zoneCount", preview.impact.zoneCount())), now);
    return new PreviewResponse(
        event.getId(), 0L, "DRAFT", request, previewHash, expiresAt,
        preview.impact, preview.conflicts, preview.warnings);
  }

  private void recordDraftRelations(Long eventId, Object request, LocalDateTime now) {
    List<Long> sourceIds;
    Target target;
    if (request instanceof MergePreviewRequest merge) {
      sourceIds = merge.sourceFarmIds();
      target = merge.target();
    } else {
      SplitPreviewRequest split = (SplitPreviewRequest) request;
      sourceIds = List.of(split.sourceFarmId());
      target = split.target();
    }
    for (Long sourceId : sourceIds) {
      StructureItemRow item = new StructureItemRow();
      item.setEventId(eventId);
      item.setItemType("PREVIEW_RELATION");
      item.setSourceFarmId(sourceId);
      item.setAction("SOURCE");
      item.setCreatedAt(now);
      mapper.insertItem(item);
    }
    if ("EXISTING".equals(target.mode())) {
      StructureItemRow item = new StructureItemRow();
      item.setEventId(eventId);
      item.setItemType("PREVIEW_RELATION");
      item.setTargetFarmId(target.farmId());
      item.setAction("TARGET");
      item.setCreatedAt(now);
      mapper.insertItem(item);
    }
  }

  private void recordCancelRelations(Long cancelEventId, List<StructureItemRow> originalItems,
                                     LocalDateTime now) {
    originalItems.stream().filter(item -> !"TARGET".equals(item.getItemType()))
        .map(StructureItemRow::getSourceFarmId).filter(Objects::nonNull).distinct().sorted()
        .forEach(sourceId -> {
          StructureItemRow relation = new StructureItemRow();
          relation.setEventId(cancelEventId);
          relation.setItemType("PREVIEW_RELATION");
          relation.setSourceFarmId(sourceId);
          relation.setAction("SOURCE");
          relation.setCreatedAt(now);
          mapper.insertItem(relation);
        });
    Long targetFarmId = targetId(originalItems);
    if (targetFarmId != null) {
      StructureItemRow relation = new StructureItemRow();
      relation.setEventId(cancelEventId);
      relation.setItemType("PREVIEW_RELATION");
      relation.setTargetFarmId(targetFarmId);
      relation.setAction("TARGET");
      relation.setCreatedAt(now);
      mapper.insertItem(relation);
    }
  }
  private PreviewResponse existingPreview(StructureEventRow e,String requestHash){if(!Objects.equals(e.getRequestHash(),requestHash))throw new BusinessException(ErrorCode.STRUCTURE_IDEMPOTENCY_CONFLICT);String status=e.getPreviewExpiresAt()!=null&&e.getPreviewExpiresAt().compareTo(LocalDateTime.now())<=0?"EXPIRED":e.getStatus();return new PreviewResponse(e.getId(),e.getVersion(),status,json.readObject(e.getRequestSnapshotJson()),e.getPreviewHash(),e.getPreviewExpiresAt(),json.read(e.getImpactJson(),Impact.class),json.readConflicts(e.getConflictsJson()),json.readWarnings(e.getWarningsJson()));}
  private String hashPreview(Object request,Preview p){return json.hash(Map.of("request",request,"impact",p.impact,"conflicts",p.conflicts,"warnings",p.warnings,"farmIds",p.relatedFarmIds,"zoneIds",p.zoneIds,"farms",mapper.findFarmsByIds(p.relatedFarmIds).stream().map(f->List.of(f.getId(),f.getStructureVersion(),f.getLifecycleStatus(),f.getStatus())).toList(),"zones",p.zoneIds.isEmpty()?List.of():mapper.findCurrentZonesByIds(p.zoneIds)));}
  private void validateFarms(Long userId,List<StructureFarmRow> fs){Long org=fs.get(0).getOrganizationId();if(fs.stream().anyMatch(f->!Objects.equals(f.getOrganizationId(),org)||!"ACTIVE".equals(f.getStatus())||!"ACTIVE".equals(f.getLifecycleStatus())))throw validation("같은 ACTIVE 조직의 ACTIVE 농장만 선택할 수 있습니다.");access.requireAll(userId,org,fs.stream().map(StructureFarmRow::getId).toList());}
  private boolean activeSourceOwner(List<Long> sourceIds,Long ownerUserId){return sourceIds.stream().anyMatch(farmId->mapper.findActiveMembers(farmId).stream().anyMatch(member->Objects.equals(l(member,"user_id"),ownerUserId)&&"FARM_OWNER".equals(s(member,"role"))&&"ACTIVE".equals(s(member,"status"))));}
  /** conflict resolution을 실제 target write와 source->target mapping item에 동일하게 적용한다. */
  private void applyMasterPlan(
      Long eventId, List<Long> sourceFarmIds, Long targetFarmId, Long organizationId,
      List<String> scopes, List<Resolution> resolutions, Long userId, LocalDateTime now) {
    List<Long> allFarmIds = new ArrayList<>(sourceFarmIds);
    if (!allFarmIds.contains(targetFarmId)) allFarmIds.add(targetFarmId);
    Map<String, List<Map<String, Object>>> groups = new TreeMap<>();
    for (Map<String, Object> row : mapper.findMasterResources(allFarmIds, scopes)) {
      groups.computeIfAbsent(masterKey(row), ignored -> new ArrayList<>()).add(row);
    }
    Map<String, Resolution> resolutionById = new HashMap<>();
    for (Resolution resolution : resolutions) resolutionById.put(resolution.conflictId(), resolution);

    for (var entry : groups.entrySet()) {
      List<Map<String, Object>> sources = entry.getValue().stream()
          .filter(row -> sourceFarmIds.contains(l(row, "farm_id")))
          .filter(row -> !Objects.equals(l(row, "farm_id"), targetFarmId))
          .filter(row -> "Y".equals(s(row, "active")))
          .sorted(Comparator.comparing((Map<String, Object> row) -> l(row, "farm_id"))
              .thenComparing(row -> l(row, "resource_id")))
          .toList();
      if (sources.isEmpty()) continue;
      List<Map<String, Object>> targets = entry.getValue().stream()
          .filter(row -> Objects.equals(l(row, "farm_id"), targetFarmId)).toList();
      Resolution resolution = resolutionById.get(conflictId(entry.getKey(), sources));

      if (resolution != null && "SKIP".equals(resolution.action())) {
        sources.forEach(source -> insertMasterMapping(
            eventId, source, targetFarmId, null, "SKIP", false, false, now));
        continue;
      }

      Map<String, Object> target;
      String mappingAction;
      boolean created = false;
      boolean reactivated = false;
      if (resolution != null && "REUSE_TARGET".equals(resolution.action())) {
        target = targets.stream().filter(row -> Objects.equals(
            l(row, "resource_id"), resolution.targetResourceId())).findFirst().orElseThrow();
        if (!"Y".equals(s(target, "active"))) {
          mapper.reactivateMasterResource(s(target, "resource_type"), s(target, "name"),
              targetFarmId, l(target, "resource_id"), userId, now);
          reactivated = true;
          target = new HashMap<>(target);
          target.put("active", "Y");
          target.put("deleted", "N");
        }
        mappingAction = "REUSE_TARGET";
      } else {
        Map<String, Object> canonical = resolution != null
                && "REUSE_CANONICAL_SOURCE".equals(resolution.action())
            ? sources.stream().filter(row -> Objects.equals(
                l(row, "resource_id"), resolution.canonicalSourceResourceId())).findFirst().orElseThrow()
            : sources.get(0);
        String targetName = resolution != null && "COPY_RENAMED".equals(resolution.action())
            ? renamedMasterName(s(canonical, "name"), resolution.newName().trim())
            : s(canonical, "name");
        if (resolution != null && "COPY_RENAMED".equals(resolution.action())
            && findMasterByName(mapper.findMasterResources(List.of(targetFarmId), scopes), targetName) != null) {
          throw validation("COPY_RENAMED 이름이 target 기준정보와 중복됩니다.");
        }
        target = findMasterByName(targets, targetName);
        if (target == null) {
          Long renamedCatalogId = resolution != null && "COPY_RENAMED".equals(resolution.action())
              ? ensureRenamedCatalog(canonical, resolution.newName().trim()) : null;
          mapper.copyMasterResource(s(canonical, "resource_type"), s(canonical, "name"),
              l(canonical, "farm_id"), l(canonical, "resource_id"), targetFarmId,
              organizationId, targetName, renamedCatalogId, userId, now);
          target = findMasterByName(mapper.findMasterResources(List.of(targetFarmId), scopes), targetName);
          if (target == null) throw new BusinessException(ErrorCode.CONFLICT, "기준정보 복사 결과를 확인할 수 없습니다.");
          created = true;
        }
        mappingAction = resolution == null ? (created ? "CREATE" : "MAP_BY_MATCH") : resolution.action();
      }
      Map<String, Object> mappedTarget = target;
      boolean eventCreated = created;
      boolean eventReactivated = reactivated;
      sources.forEach(source -> insertMasterMapping(
          eventId, source, targetFarmId, mappedTarget, mappingAction,
          eventCreated, eventReactivated, now));
    }
  }

  private void insertMasterMapping(
      Long eventId, Map<String, Object> source, Long targetFarmId, Map<String, Object> target,
      String action, boolean created, boolean reactivated, LocalDateTime now) {
    StructureItemRow item = new StructureItemRow();
    item.setEventId(eventId);
    item.setItemType("MASTER");
    item.setSourceFarmId(l(source, "farm_id"));
    item.setTargetFarmId(targetFarmId);
    item.setResourceType(s(source, "resource_type"));
    item.setSourceResourceId(l(source, "resource_id"));
    item.setTargetResourceId(target == null ? null : l(target, "resource_id"));
    item.setAction(action);
    item.setBeforeJson(json.write(Map.of(
        "name", s(source, "name"), "active", s(source, "active"))));
    if (target != null) item.setAfterJson(json.write(Map.of(
        "name", s(target, "name"), "active", s(target, "active"),
        "createdByEvent", created, "reactivatedByEvent", reactivated)));
    item.setCreatedAt(now);
    mapper.insertItem(item);
  }

  private String masterKey(Map<String, Object> row) {
    return s(row, "resource_type") + ":" + s(row, "name").trim().toLowerCase(Locale.ROOT);
  }

  private String conflictId(String key, List<Map<String, Object>> sources) {
    return json.hash(Map.of("key", key, "sources", sources.stream()
        .map(row -> List.of(l(row, "farm_id"), l(row, "resource_id"))).toList())).substring(0, 24);
  }

  private Map<String, Object> findMasterByName(List<Map<String, Object>> rows, String name) {
    String match = name.trim().toLowerCase(Locale.ROOT);
    return rows.stream().filter(row -> s(row, "name").trim().toLowerCase(Locale.ROOT).equals(match))
        .findFirst().orElse(null);
  }

  private String renamedMasterName(String original, String newName) {
    if (original.startsWith("CROP:")) return "CROP:" + newName;
    if (original.startsWith("VARIETY:")) return original.substring(0, original.lastIndexOf(':') + 1) + newName;
    return newName;
  }

  private Long ensureRenamedCatalog(Map<String, Object> source, String newName) {
    String name = s(source, "name");
    if (name.startsWith("CROP:")) {
      mapper.insertCropCatalogIfAbsent(newName);
      return mapper.findCropCatalogId(newName).orElseThrow();
    }
    if (name.startsWith("VARIETY:")) {
      Long cropId = Long.valueOf(name.split(":", 3)[1]);
      mapper.insertVarietyCatalogIfAbsent(cropId, newName);
      return mapper.findVarietyCatalogId(cropId, newName).orElseThrow();
    }
    return null;
  }

  private boolean masterChanged(String afterJson) {
    if (afterJson == null) return false;
    Object value = json.readObject(afterJson);
    if (!(value instanceof Map<?, ?> map)) return false;
    return Boolean.TRUE.equals(map.get("createdByEvent"))
        || Boolean.TRUE.equals(map.get("reactivatedByEvent"));
  }

  private String masterName(String afterJson) {
    Object value = json.readObject(afterJson);
    return String.valueOf(((Map<?, ?>) value).get("name"));
  }
  private void recordMemberItems(Long event,Long target,LocalDateTime now){for(Map<String,Object>m:mapper.findActiveMembers(target)){StructureItemRow i=new StructureItemRow();i.setEventId(event);i.setItemType("MEMBER");i.setTargetFarmId(target);i.setResourceType("FARM_MEMBER");i.setTargetResourceId(l(m,"id"));i.setAction("CREATE");i.setAfterJson(json.write(m));i.setCreatedAt(now);mapper.insertItem(i);}}
  private void moveZone(StructureEventRow e,Long user,Long target,Map<String,Object> z,LocalDateTime now){Long zid=l(z,"zone_id"),source=l(z,"farm_id"),zv=l(z,"zone_version"),aid=l(z,"assignment_id"),av=l(z,"assignment_version");LocalDate from=date(z,"effective_from");if(e.getEffectiveDate().isBefore(from))throw new BusinessException(ErrorCode.PERIOD_CONFLICT);if(mapper.moveZone(zid,source,target,zv,user,now)!=1)throw new BusinessException(ErrorCode.PREVIEW_STALE);if(e.getEffectiveDate().equals(from)){if(mapper.moveAssignmentInPlace(aid,av,target,e.getId(),user,now)!=1)throw new BusinessException(ErrorCode.PERIOD_CONFLICT);}else{if(mapper.closeAssignment(aid,av,e.getEffectiveDate(),e.getId(),user,now)!=1)throw new BusinessException(ErrorCode.PERIOD_CONFLICT);zones.insertAssignment(FarmZoneAssignmentEntity.builder().organizationId(e.getOrganizationId()).zoneId(zid).farmId(target).effectiveFrom(e.getEffectiveDate()).changeEventId(e.getId()).activeYn("Y").version(0L).createdBy(user).createdAt(now).build());}StructureItemRow item=new StructureItemRow();item.setEventId(e.getId());item.setItemType("ZONE");item.setSourceFarmId(source);item.setTargetFarmId(target);item.setZoneId(zid);item.setAction(e.getEffectiveDate().equals(from)?"MOVE_IN_PLACE":"CLOSE_APPEND");item.setPeriodStart(e.getEffectiveDate());item.setBeforeJson(json.write(Map.of("farmId",source,"assignmentId",aid,"effectiveFrom",from)));item.setAfterJson(json.write(Map.of("farmId",target,"effectiveFrom",e.getEffectiveDate())));item.setCreatedAt(now);mapper.insertItem(item);}
  private void insertFarmItem(Long event,StructureFarmRow f,Long target,String type,LocalDateTime now){StructureItemRow i=new StructureItemRow();i.setEventId(event);i.setItemType("FARM");i.setSourceFarmId(f.getId());i.setTargetFarmId(target);i.setAction("MERGE".equals(type)&&!Objects.equals(f.getId(),target)?"MARK_MERGED":"MOVE_ZONES");i.setBeforeJson(json.write(Map.of("lifecycle",f.getLifecycleStatus(),"structureVersion",f.getStructureVersion())));i.setAfterJson(json.write(Map.of("targetFarmId",target)));i.setCreatedAt(now);mapper.insertItem(i);}
  private void insertTargetItem(Long event,Long targetId,boolean created,Target target,LocalDateTime now){StructureItemRow i=new StructureItemRow();i.setEventId(event);i.setItemType("TARGET");i.setTargetFarmId(targetId);i.setAction(created?"CREATE":"REUSE_TARGET");i.setAfterJson(json.write(Map.of("mode",target.mode(),"ownerUserId",target.ownerUserId()==null?0:target.ownerUserId())));i.setCreatedAt(now);mapper.insertItem(i);}
  private String fingerprint(List<Long> ids,boolean newTarget,Long target){List<Long> zoneIds=mapper.findCurrentZoneIds(ids);Map<String,Object> state=new TreeMap<>();state.put("farms",mapper.findFarmsByIds(ids).stream().map(f->List.of(f.getId(),f.getLifecycleStatus(),f.getStatus(),f.getOwnerUserId(),f.getStructureVersion())).toList());state.put("zones",zoneIds.isEmpty()?List.of():mapper.findCurrentZonesByIds(zoneIds));state.put("members",ids.stream().flatMap(id->mapper.findActiveMembers(id).stream()).toList());state.put("masters",mapper.findMasterResources(ids,SCOPES.stream().sorted().toList()));state.put("newTargetDependentCount",newTarget?mapper.countBlockingDependents(target):0);return json.hash(state);}
  private EventDetail detailAuthorized(Long userId, StructureEventRow event) {
    List<StructureItemRow> relationItems = mapper.findItems(event.getId());
    Object snapshot = json.readObject(event.getRequestSnapshotJson());
    if ("CANCEL".equals(event.getEventType()) && event.getReversesEventId() != null) {
      StructureEventRow original = mapper.findEvent(event.getReversesEventId()).orElseThrow();
      relationItems = mapper.findItems(original.getId());
      snapshot = json.readObject(original.getRequestSnapshotJson());
    }
    List<Long> sourceIds = relationItems.stream()
        .filter(item -> !"TARGET".equals(item.getItemType()))
        .map(StructureItemRow::getSourceFarmId)
        .filter(Objects::nonNull)
        .distinct().sorted().toList();
    if (sourceIds.isEmpty() && snapshot instanceof Map<?, ?> map) {
      Object many = map.get("sourceFarmIds");
      if (many instanceof List<?> values) {
        sourceIds = values.stream().map(value -> ((Number) value).longValue()).sorted().toList();
      } else if (map.get("sourceFarmId") instanceof Number one) {
        sourceIds = List.of(one.longValue());
      }
    }
    Target requestedTarget = targetFromSnapshot(snapshot);
    Long targetId = targetId(relationItems);
    if (targetId == null && requestedTarget != null) targetId = requestedTarget.farmId();
    List<Long> related = new ArrayList<>(sourceIds);
    if (targetId != null) related.add(targetId);
    related = related.stream().distinct().sorted().toList();
    try {
      access.requireAll(userId, event.getOrganizationId(), related);
    } catch (BusinessException denied) {
      throw new BusinessException(ErrorCode.FARM_STRUCTURE_EVENT_NOT_FOUND);
    }
    Map<Long, StructureFarmRow> farmsById = new HashMap<>();
    if (!related.isEmpty()) mapper.findFarmsByIds(related).forEach(farm -> farmsById.put(farm.getId(), farm));
    FarmIdentity targetFarm = targetId == null ? null : identity(farmsById.get(targetId));
    EventTarget target = new EventTarget(
        requestedTarget == null ? "EXISTING" : requestedTarget.mode(),
        targetFarm,
        requestedTarget == null ? null : requestedTarget.name(),
        requestedTarget == null ? null : requestedTarget.ownerUserId());
    String status = "DRAFT".equals(event.getStatus()) && event.getPreviewExpiresAt() != null
        && event.getPreviewExpiresAt().compareTo(LocalDateTime.now()) <= 0 ? "EXPIRED" : event.getStatus();
    boolean cancellable = "CONFIRMED".equals(status) && event.getReversedByEventId() == null
        && !"CANCEL".equals(event.getEventType());
    return new EventDetail(
        event.getId(), event.getVersion(),
        new OrganizationIdentity(event.getOrganizationId(), event.getOrganizationName(),
            event.getOrganizationType(), event.getOrganizationStatus()),
        event.getEventType(), event.getEventName(), event.getEffectiveDate(), status,
        event.getReportPolicy(), event.getPeriodStart() == null ? null
            : new SelectedPeriod(event.getPeriodStart(), event.getPeriodEndExclusive()),
        sourceIds.stream().map(farmsById::get).filter(Objects::nonNull).map(this::identity).toList(),
        target, snapshot, json.read(event.getImpactJson(), Impact.class),
        json.readConflicts(event.getConflictsJson()), json.readWarnings(event.getWarningsJson()),
        new Actor(event.getCreatedBy(), event.getCreatedByName()), event.getCreatedAt(),
        event.getConfirmedBy() == null ? null : new Actor(event.getConfirmedBy(), event.getConfirmedByName()),
        event.getConfirmedAt(), event.getPreviewExpiresAt(), event.getPreviewHash(), cancellable,
        cancellable ? null : "현재 상태에서는 취소할 수 없습니다.",
        event.getReversesEventId(), event.getReversedByEventId(), targetId, related);
  }
  private Target targetFromSnapshot(Object snap){if(!(snap instanceof Map<?,?>m)||!(m.get("target") instanceof Map<?,?>t))return null;return new Target(String.valueOf(t.get("mode")),num(t.get("farmId")),t.get("name")==null?null:String.valueOf(t.get("name")),num(t.get("ownerUserId")));}
  private EventSummary summary(EventDetail d){return new EventSummary(d.id(),d.version(),d.organization(),d.eventType(),d.eventName(),d.effectiveDate(),d.status(),d.reportPolicy(),d.selectedPeriod(),d.sourceFarms(),d.targetFarm(),d.createdBy(),d.createdAt(),d.confirmedBy(),d.confirmedAt(),d.expiresAt(),d.previewHash(),d.cancellable(),d.cancelBlockedReason(),d.reversesEventId(),d.reversedByEventId());}
  private FarmIdentity identity(StructureFarmRow f){return f==null?null:new FarmIdentity(f.getId(),f.getOrganizationId(),f.getFarmCode(),f.getName(),f.getLifecycleStatus(),f.getStatus(),f.getStructureVersion(),f.getCreatedAt());}
  private MergePreviewRequest normalize(MergePreviewRequest r){if(r==null)throw validation("요청 본문이 필요합니다.");uuid(r.clientRequestId());List<Long>src=ids(r.sourceFarmIds(),2,20);Target t=target(r.target(),src,false);return new MergePreviewRequest(r.clientRequestId(),src,t,date(r.effectiveDate()),policy(r.reportPolicy(),r.selectedPeriod()),period(r.reportPolicy(),r.selectedPeriod()),scopes(r.masterDataScopes()),resolutions(r.masterDataResolutions()),trim(r.reason()));}
  private SplitPreviewRequest normalize(SplitPreviewRequest r){if(r==null||r.sourceFarmId()==null)throw validation("sourceFarmId가 필요합니다.");uuid(r.clientRequestId());List<Long>z=ids(r.zoneIds(),1,100);Target t=target(r.target(),List.of(r.sourceFarmId()),true);return new SplitPreviewRequest(r.clientRequestId(),r.sourceFarmId(),t,z,date(r.effectiveDate()),policy(r.reportPolicy(),r.selectedPeriod()),period(r.reportPolicy(),r.selectedPeriod()),scopes(r.masterDataScopes()),resolutions(r.masterDataResolutions()),trim(r.reason()));}
  private Target target(Target t,List<Long>src,boolean split){if(t==null||t.mode()==null||!Set.of("EXISTING","NEW").contains(t.mode()))throw validation("target mode가 올바르지 않습니다.");if("EXISTING".equals(t.mode())){if(t.farmId()==null||t.name()!=null||t.ownerUserId()!=null)throw validation("EXISTING target 필드를 확인해주세요.");if(split&&src.contains(t.farmId()))throw validation("분리 target은 source와 달라야 합니다.");}else{if(t.farmId()!=null||t.ownerUserId()==null||t.name()==null||t.name().trim().isEmpty()||t.name().trim().length()>100)throw validation("NEW target 이름과 ownerUserId가 필요합니다.");}return new Target(t.mode(),t.farmId(),t.name()==null?null:t.name().trim(),t.ownerUserId());}
  private String policy(String p,SelectedPeriod sp){if(p==null||!POLICIES.contains(p))throw validation("reportPolicy가 올바르지 않습니다.");period(p,sp);return p;}
  private SelectedPeriod period(String p,SelectedPeriod x){if("SELECTED_PERIOD".equals(p)){if(x==null||x.from()==null||x.toExclusive()==null||!x.from().isBefore(x.toExclusive())||x.toExclusive().isAfter(x.from().plusYears(10)))throw validation("선택 기간을 확인해주세요.");return x;}if(x!=null)throw validation("SELECTED_PERIOD에서만 selectedPeriod를 사용할 수 있습니다.");return null;}
  private List<String> scopes(List<String>x){if(x==null||x.isEmpty())throw validation("masterDataScopes가 필요합니다.");if(x.stream().anyMatch(Objects::isNull))throw validation("masterDataScopes가 올바르지 않습니다.");LinkedHashSet<String>s=new LinkedHashSet<>(x);if(s.size()!=x.size()||!SCOPES.containsAll(s))throw validation("masterDataScopes가 올바르지 않습니다.");if(s.contains("SEASON"))s.add("CROP_VARIETY");return s.stream().sorted().toList();}
  private List<Resolution> resolutions(List<Resolution>x){if(x==null)return List.of();if(x.stream().anyMatch(r->r==null||r.conflictId()==null))throw validation("충돌 resolution ID를 확인해주세요.");return x.stream().sorted(Comparator.comparing(Resolution::conflictId)).toList();}
  private List<Long> ids(List<Long>x,int min,int max){if(x==null||x.size()<min||x.size()>max||x.stream().anyMatch(Objects::isNull)||x.stream().distinct().count()!=x.size())throw validation("ID 목록의 개수와 중복을 확인해주세요.");return x.stream().sorted().toList();}
  private LocalDate date(LocalDate d){if(d==null||d.isAfter(LocalDate.now(SEOUL)))throw validation("기준일은 서울 기준 오늘 이하여야 합니다.");return d;}
  private void uuid(String x){try{UUID.fromString(x);}catch(Exception e){throw validation("clientRequestId는 UUID여야 합니다.");}}
  private String trim(String s){return s==null||s.isBlank()?null:s.trim();}
  private List<Long> related(List<Long>s,Target t){ArrayList<Long>x=new ArrayList<>(s);if("EXISTING".equals(t.mode()))x.add(t.farmId());return x.stream().distinct().sorted().toList();}
  private boolean isNew(List<StructureItemRow>i){return i.stream().anyMatch(x->"TARGET".equals(x.getItemType())&&"CREATE".equals(x.getAction()));}
  private Long targetId(List<StructureItemRow>i){return i.stream().map(StructureItemRow::getTargetFarmId).filter(Objects::nonNull).findFirst().orElse(null);}
  private Object v(Map<String,Object>m,String k){return m.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(k)).map(Map.Entry::getValue).findFirst().orElse(null);}private String s(Map<String,Object>m,String k){Object x=v(m,k);return x==null?null:x.toString();}private Long l(Map<String,Object>m,String k){Object x=v(m,k);return x==null?null:((Number)x).longValue();}private long n(Map<String,Object>m,String k){Long x=l(m,k);return x==null?0:x;}private LocalDate date(Map<String,Object>m,String k){Object x=v(m,k);return x instanceof LocalDate d?d:LocalDate.parse(x.toString());}private Long num(Object x){return x instanceof Number n?n.longValue():null;}
  private BusinessException validation(String m){return new BusinessException(ErrorCode.VALIDATION_FAILED,m);}
  private record Preview(Impact impact,List<Conflict> conflicts,List<Warning> warnings,List<Long> relatedFarmIds,List<Long> zoneIds){}
}
