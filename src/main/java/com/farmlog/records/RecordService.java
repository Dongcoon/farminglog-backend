package com.farmlog.records;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMembership;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.entity.FarmMemberEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmaccess.CareAccessGuard;
import com.farmlog.farmaccess.entity.CareAssignmentRow;
import com.farmlog.masterdata.entity.CropSeasonEntity;
import com.farmlog.masterdata.mapper.MasterDataMapper;
import com.farmlog.records.dto.*;
import com.farmlog.records.entity.FeedRow;
import com.farmlog.records.entity.RecordRow;
import com.farmlog.records.entity.ReferenceRow;
import com.farmlog.records.mapper.RecordMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Phase 4 네 기록의 권한·FK·금액·멱등·낙관적 잠금을 한 곳에서 강제한다. */
@Service
public class RecordService {
  private static final String YES = "Y", NO = "N", NOT_REQUIRED = "NOT_REQUIRED";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Set<String> WRITERS =
      Set.of(
          FarmMemberEntity.ROLE_FARM_OWNER,
          FarmMemberEntity.ROLE_FARM_MANAGER,
          FarmMemberEntity.ROLE_WORKER,
          "FARM_CARE_MANAGER");
  private static final Set<String> MANAGERS =
      Set.of(FarmMemberEntity.ROLE_FARM_OWNER, FarmMemberEntity.ROLE_FARM_MANAGER);
  private static final Map<RecordType, Set<String>> BULK_ALLOWED =
      Map.of(
          RecordType.WORK, Set.of("zoneId", "cropId", "varietyId", "seasonId", "workTypeId"),
          RecordType.PEST_CONTROL, Set.of("zoneId", "cropId", "varietyId", "seasonId"),
          RecordType.HARVEST, Set.of("zoneId", "cropId", "varietyId", "seasonId", "grade", "unit"),
          RecordType.SALES, Set.of("customerId", "settlementStatus"));
  private static final Map<RecordType, Set<String>> MUTATION_ALLOWED =
      Map.of(
          RecordType.WORK,
              Set.of(
                  "workDate",
                  "zoneId",
                  "cropId",
                  "varietyId",
                  "seasonId",
                  "workTypeId",
                  "workerCount",
                  "workHours",
                  "memo"),
          RecordType.PEST_CONTROL,
              Set.of(
                  "applyDate",
                  "zoneId",
                  "cropId",
                  "varietyId",
                  "seasonId",
                  "chemicalName",
                  "targetPest",
                  "dilutionRatio",
                  "amountValue",
                  "amountUnit",
                  "preharvestIntervalDays",
                  "memo"),
          RecordType.HARVEST,
              Set.of(
                  "harvestDate",
                  "zoneId",
                  "cropId",
                  "varietyId",
                  "seasonId",
                  "grade",
                  "quantity",
                  "unit",
                  "packageUnit",
                  "memo"),
          RecordType.SALES,
              Set.of(
                  "salesDate",
                  "customerId",
                  "itemName",
                  "quantity",
                  "unit",
                  "unitPrice",
                  "feeAmount",
                  "netAmountOverride",
                  "settlementStatus",
                  "memo"));

  private final RecordMapper mapper;
  private final FarmMapper farmMapper;
  private final MasterDataMapper masterDataMapper;
  private final FarmAccessGuard accessGuard;
  private final CareAccessGuard careAccessGuard;
  private final FarmMutationGuard mutationGuard;

  @Autowired
  public RecordService(
      RecordMapper mapper,
      FarmMapper farmMapper,
      MasterDataMapper masterDataMapper,
      FarmAccessGuard accessGuard,
      CareAccessGuard careAccessGuard,
      FarmMutationGuard mutationGuard) {
    this.mapper = mapper;
    this.farmMapper = farmMapper;
    this.masterDataMapper = masterDataMapper;
    this.accessGuard = accessGuard;
    this.careAccessGuard = careAccessGuard;
    this.mutationGuard = mutationGuard;
  }

  public RecordService(
      RecordMapper mapper,
      FarmMapper farmMapper,
      MasterDataMapper masterDataMapper,
      FarmAccessGuard accessGuard,
      CareAccessGuard careAccessGuard) {
    this(mapper, farmMapper, masterDataMapper, accessGuard, careAccessGuard,
        new FarmMutationGuard(farmMapper));
  }

  public RecordService(
      RecordMapper mapper,
      FarmMapper farmMapper,
      MasterDataMapper masterDataMapper,
      FarmAccessGuard accessGuard) {
    this(mapper, farmMapper, masterDataMapper, accessGuard, null);
  }

  public PageResponse<RecordResponse> list(
      Long userId,
      Long farmId,
      RecordType type,
      LocalDate from,
      LocalDate to,
      Long zoneId,
      Long cropId,
      Long varietyId,
      Long seasonId,
      Long createdBy,
      Long workTypeId,
      Long customerId,
      String settlementStatus,
      int page,
      int size,
      String sort) {
    FarmMembership membership = access(userId, farmId).membership();
    validatePage(page, size);
    validateDateRange(from, to);
    validateDomainFilters(
        type, zoneId, cropId, varietyId, seasonId, workTypeId, customerId, settlementStatus);
    SortSpec spec = sort(type, sort);
    RecordFilter filter =
        new RecordFilter(
            farmId,
            from,
            to,
            zoneId,
            cropId,
            varietyId,
            seasonId,
            createdBy,
            workTypeId,
            customerId,
            settlementStatus,
            page,
            size,
            spec.column,
            spec.direction);
    List<RecordResponse> content =
        mapper.findPage(type, filter).stream().map(r -> response(r, membership)).toList();
    return PageResponse.of(content, page, size, mapper.countPage(type, filter));
  }

  public RecordResponse detail(Long userId, Long farmId, RecordType type, Long id) {
    FarmMembership membership = access(userId, farmId).membership();
    return response(get(type, farmId, id), membership);
  }

  @Transactional
  public RecordResponse create(
      Long userId, Long farmId, RecordType type, RecordMutationRequest request) {
    AccessContext context = accessForWrite(userId, farmId);
    FarmMembership membership = context.membership();
    requireRole(membership, WRITERS);
    validateMutationFields(type, request, true);
    validateClientRequestId(request.clientRequestId());
    if (request.clientRequestId() != null) {
      Optional<RecordRow> existing =
          mapper.findByClientRequestId(type, farmId, request.clientRequestId());
      if (existing.isPresent()) return response(existing.get(), membership);
    }
    FarmEntity farm =
        farmMapper
            .findById(farmId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    RecordRow row = new RecordRow();
    row.setType(type);
    row.setFarmId(farmId);
    row.setOrganizationId(farm.getOrganizationId());
    row.setCreatedBy(userId);
    row.setCreatedRole(membership.role());
    row.setFarmerConfirmStatus(NOT_REQUIRED);
    if ("FARM_CARE_MANAGER".equals(membership.role())) {
      row.setCareAssignmentId(context.assignment().getId());
      row.setFarmerConfirmStatus("PENDING");
    }
    row.setClientRequestId(request.clientRequestId());
    row.setCreatedAt(LocalDateTime.now());
    row.setVersion(0L);
    apply(row, request, true);
    validate(row, true, allFkFields(type));
    try {
      if (insert(row) != 1) throw new BusinessException(ErrorCode.CONFLICT, "기록을 저장하지 못했습니다.");
    } catch (DuplicateKeyException duplicate) {
      if (request.clientRequestId() == null) throw duplicate;
      return response(
          mapper
              .findByClientRequestIdForUpdate(type, farmId, request.clientRequestId())
              .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "동일 요청을 처리하고 있습니다.")),
          membership);
    }
    if ("FARM_CARE_MANAGER".equals(membership.role()))
      audit(
          row,
          userId,
          "CARE_RECORD_CREATED",
          "{\"careAssignmentId\":" + row.getCareAssignmentId() + "}");
    return response(get(type, farmId, row.getId()), membership);
  }

  @Transactional
  public RecordResponse update(
      Long userId, Long farmId, RecordType type, Long id, RecordMutationRequest request) {
    AccessContext context = accessForWrite(userId, farmId);
    FarmMembership membership = context.membership();
    validateMutationFields(type, request, false);
    RecordRow row = get(type, farmId, id);
    requireMutation(context, row);
    if (request.version() == null) throw validation("version은 필수입니다.");
    if (!Objects.equals(request.version(), row.getVersion())) throw conflict();
    Set<String> changedFks = changedFkFields(row, request);
    apply(row, request, false);
    validate(row, false, changedFks);
    // care manager가 만든 기록은 누가 수정하더라도 농가가 다시 확인해야 한다.
    if ("FARM_CARE_MANAGER".equals(row.getCreatedRole())) {
      row.setFarmerConfirmStatus("PENDING");
      row.setFarmerConfirmedBy(null);
      row.setFarmerConfirmedAt(null);
    }
    row.setUpdatedBy(userId);
    row.setUpdatedAt(LocalDateTime.now());
    if (update(row, request.version()) != 1) throw conflict();
    audit(
        row,
        userId,
        "FARM_CARE_MANAGER".equals(row.getCreatedRole()) ? "CARE_RECORD_UPDATED" : "UPDATE",
        "{\"version\":" + request.version() + "}");
    return response(get(type, farmId, id), membership);
  }

  @Transactional
  public void delete(Long userId, Long farmId, RecordType type, Long id, Long version) {
    AccessContext context = accessForWrite(userId, farmId);
    RecordRow row = get(type, farmId, id);
    requireMutation(context, row);
    if (!Objects.equals(version, row.getVersion())) throw conflict();
    if (mapper.softDelete(type, farmId, id, version, userId, LocalDateTime.now()) != 1)
      throw conflict();
    audit(
        row,
        userId,
        "FARM_CARE_MANAGER".equals(row.getCreatedRole()) ? "CARE_RECORD_DELETED" : "DELETE",
        "{\"version\":" + version + "}");
  }

  @Transactional
  public BulkResponse bulk(Long userId, Long farmId, RecordType type, BulkRequest request) {
    FarmMembership membership = access(userId, farmId).membership();
    requireRole(membership, MANAGERS);
    mutationGuard.lockActiveFarm(farmId);
    membership = accessGuard.requireFarmRoleForUpdate(
        userId, farmId, "FARM_OWNER", "FARM_MANAGER");
    if (request == null
        || request.operation() == null
        || request.items() == null
        || request.items().isEmpty()) throw validation("operation과 items는 필수입니다.");
    if (request.items().size() > 100) throw validation("일괄 처리는 최대 100건입니다.");
    if (request.items().stream()
        .anyMatch(item -> item == null || item.id() == null || item.version() == null))
      throw validation("일괄 처리 ID와 version은 필수입니다.");
    if (request.items().stream().map(BulkRequest.Item::id).distinct().count()
        != request.items().size()) throw validation("중복 ID가 있습니다.");
    if (request.operation() == BulkRequest.Operation.UPDATE)
      validateBulkChanges(type, request.changes());
    if (request.operation() == BulkRequest.Operation.DELETE && request.changes() != null)
      throw validation("일괄 삭제에는 changes를 사용할 수 없습니다.");
    List<RecordRow> rows = new ArrayList<>();
    for (BulkRequest.Item item : request.items()) {
      RecordRow row = get(type, farmId, item.id());
      if (!Objects.equals(row.getVersion(), item.version())) throw conflict();
      if (request.operation() == BulkRequest.Operation.UPDATE) {
        Set<String> changed = changedFkFields(row, request.changes());
        apply(row, request.changes(), false);
        validate(row, false, changed);
        if ("FARM_CARE_MANAGER".equals(row.getCreatedRole())) {
          row.setFarmerConfirmStatus("PENDING");
          row.setFarmerConfirmedBy(null);
          row.setFarmerConfirmedAt(null);
        }
      }
      rows.add(row);
    }
    if (request.operation() == BulkRequest.Operation.DELETE) {
      List<Long> ids = new ArrayList<>();
      for (int i = 0; i < rows.size(); i++) {
        RecordRow row = rows.get(i);
        long version = request.items().get(i).version();
        if (mapper.softDelete(type, farmId, row.getId(), version, userId, LocalDateTime.now()) != 1)
          throw conflict();
        audit(
            row,
            userId,
            "FARM_CARE_MANAGER".equals(row.getCreatedRole())
                ? "CARE_RECORD_DELETED"
                : "BULK_DELETE",
            "{\"version\":" + version + ",\"bulk\":true}");
        ids.add(row.getId());
      }
      return BulkResponse.deleted(ids);
    }
    List<RecordResponse> content = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      RecordRow row = rows.get(i);
      long version = request.items().get(i).version();
      row.setUpdatedBy(userId);
      row.setUpdatedAt(LocalDateTime.now());
      if (update(row, version) != 1) throw conflict();
      audit(
          row,
          userId,
          "FARM_CARE_MANAGER".equals(row.getCreatedRole()) ? "CARE_RECORD_UPDATED" : "BULK_UPDATE",
          "{\"version\":" + version + ",\"bulk\":true}");
      content.add(response(get(type, farmId, row.getId()), membership));
    }
    return BulkResponse.updated(content);
  }

  public PageResponse<FeedResponse> feed(
      Long userId,
      Long farmId,
      LocalDate from,
      LocalDate to,
      Long zoneId,
      Long createdBy,
      List<RecordType> types,
      int page,
      int size,
      String sort) {
    FarmMembership membership = access(userId, farmId).membership();
    validatePage(page, size);
    validateDateRange(from, to);
    List<RecordType> selected =
        (types == null || types.isEmpty())
            ? List.of(RecordType.values())
            : List.copyOf(new LinkedHashSet<>(types));
    SortSpec spec = feedSort(sort);
    List<FeedResponse> content =
        mapper
            .findFeed(
                farmId,
                from,
                to,
                zoneId,
                createdBy,
                selected,
                spec.column,
                spec.direction,
                page * size,
                size)
            .stream()
            .map(r -> feedResponse(r, membership))
            .toList();
    return PageResponse.of(
        content, page, size, mapper.countFeed(farmId, from, to, zoneId, createdBy, selected));
  }

  public List<RecordResponse.Actor> authors(Long userId, Long farmId) {
    access(userId, farmId);
    return mapper.findRecordAuthors(farmId).stream()
        .map(r -> actor(r.getId(), r.getName()))
        .toList();
  }

  private void apply(RecordRow row, RecordMutationRequest r, boolean create) {
    RecordType type = row.getType();
    if (type == RecordType.WORK && (create || r.has("workDate"))) row.setRecordDate(r.workDate());
    if (type == RecordType.PEST_CONTROL && (create || r.has("applyDate")))
      row.setRecordDate(r.applyDate());
    if (type == RecordType.HARVEST && (create || r.has("harvestDate")))
      row.setRecordDate(r.harvestDate());
    if (type == RecordType.SALES && (create || r.has("salesDate")))
      row.setRecordDate(r.salesDate());
    if (type != RecordType.SALES && (create || r.has("zoneId"))) row.setZoneId(r.zoneId());
    if (type != RecordType.SALES) {
      if (create || r.has("cropId")) row.setCropId(r.cropId());
      if (create || r.has("varietyId")) row.setVarietyId(r.varietyId());
      if (create || r.has("seasonId")) row.setSeasonId(r.seasonId());
    }
    if (r.has("memo") || create) row.setMemo(trimNullable(r.memo()));
    switch (type) {
      case WORK -> {
        if (create || r.has("workTypeId")) row.setWorkTypeId(r.workTypeId());
        if (create || r.has("workerCount")) row.setWorkerCount(r.workerCount());
        if (create || r.has("workHours")) row.setWorkHours(r.workHours());
      }
      case PEST_CONTROL -> {
        if (create || r.has("chemicalName")) row.setChemicalName(trimNullable(r.chemicalName()));
        if (create || r.has("targetPest")) row.setTargetPest(trimNullable(r.targetPest()));
        if (create || r.has("dilutionRatio")) row.setDilutionRatio(trimNullable(r.dilutionRatio()));
        if (create || r.has("amountValue")) row.setAmountValue(r.amountValue());
        if (create || r.has("amountUnit")) row.setAmountUnit(trimNullable(r.amountUnit()));
        if (create || r.has("preharvestIntervalDays"))
          row.setPreharvestIntervalDays(r.preharvestIntervalDays());
      }
      case HARVEST -> {
        if (create || r.has("grade")) row.setGrade(trimNullable(r.grade()));
        if (create || r.has("quantity")) row.setQuantity(r.quantity());
        if (create || r.has("unit")) row.setUnit(defaultText(r.unit(), "kg"));
        if (create || r.has("packageUnit")) row.setPackageUnit(trimNullable(r.packageUnit()));
      }
      case SALES -> applySales(row, r, create);
    }
  }

  private void applySales(RecordRow row, RecordMutationRequest r, boolean create) {
    if (create || r.has("customerId")) row.setCustomerId(r.customerId());
    if (create || r.has("itemName")) row.setItemName(trimNullable(r.itemName()));
    if (create || r.has("quantity")) row.setQuantity(r.quantity());
    if (create || r.has("unit")) row.setUnit(defaultText(r.unit(), "kg"));
    if (create || r.has("unitPrice")) row.setUnitPrice(money(r.unitPrice()));
    if (create || r.has("feeAmount"))
      row.setFeeAmount(r.feeAmount() == null ? BigDecimal.ZERO : money(r.feeAmount()));
    if (create || r.has("settlementStatus"))
      row.setSettlementStatus(defaultText(r.settlementStatus(), "PENDING"));
    if (row.getQuantity() != null && row.getUnitPrice() != null)
      row.setGrossAmount(money(row.getQuantity().multiply(row.getUnitPrice())));
    BigDecimal calculated =
        row.getGrossAmount() == null
            ? null
            : money(
                row.getGrossAmount()
                    .subtract(row.getFeeAmount() == null ? BigDecimal.ZERO : row.getFeeAmount()));
    if (r.has("netAmountOverride")) {
      if (r.netAmountOverride() == null) {
        row.setNetAmount(calculated);
        row.setNetAmountOverriddenYn(NO);
      }
      // 숫자를 명시한 것 자체가 수동 입력 의도이므로 계산값과 같아도 override 상태를 유지한다.
      else {
        row.setNetAmount(money(r.netAmountOverride()));
        row.setNetAmountOverriddenYn(YES);
      }
    } else if (create || !YES.equals(row.getNetAmountOverriddenYn())) {
      row.setNetAmount(calculated);
      row.setNetAmountOverriddenYn(NO);
    }
  }

  private void validate(RecordRow row, boolean create, Set<String> activeFields) {
    if (row.getRecordDate() == null) throw validation("업무일자는 필수입니다.");
    if (row.getRecordDate().isAfter(LocalDate.now(SEOUL))) throw validation("미래 날짜는 기록할 수 없습니다.");
    max(row.getMemo(), 2000, "메모");
    if (row.getType() != RecordType.SALES) {
      if (row.getZoneId() == null) throw validation("구역은 필수입니다.");
      if (create || activeFields.contains("zoneId"))
        requireRef(
            mapper.findCurrentZoneForUpdate(row.getFarmId(), row.getZoneId()), ErrorCode.FARM_ZONE_NOT_FOUND);
      validateCropRefs(row, create, activeFields);
    }
    switch (row.getType()) {
      case WORK -> {
        if (row.getWorkTypeId() == null) throw validation("작업유형은 필수입니다.");
        if (create || activeFields.contains("workTypeId"))
          requireRef(
              mapper.findWorkType(row.getFarmId(), row.getWorkTypeId(), true),
              ErrorCode.WORK_TYPE_NOT_FOUND);
        positive(row.getWorkerCount(), "작업 인원");
        positive(row.getWorkHours(), "작업 시간");
      }
      case PEST_CONTROL -> {
        requiredText(row.getChemicalName(), 150, "약제명");
        max(row.getTargetPest(), 150, "병해충");
        max(row.getDilutionRatio(), 50, "희석배수");
        positive(row.getAmountValue(), 12, 2, "사용량");
        if ((row.getAmountValue() == null) != (row.getAmountUnit() == null))
          throw validation("사용량과 단위는 함께 입력하거나 함께 비워주세요.");
        max(row.getAmountUnit(), 20, "사용량 단위");
        if (row.getPreharvestIntervalDays() != null && row.getPreharvestIntervalDays() < 0)
          throw validation("안전사용기간은 0 이상입니다.");
      }
      case HARVEST -> {
        positiveRequired(row.getQuantity(), "수확량");
        requiredText(row.getUnit(), 20, "단위");
        max(row.getGrade(), 50, "등급");
        max(row.getPackageUnit(), 50, "포장단위");
        decimal(row.getQuantity(), 12, 2, "수확량");
      }
      case SALES -> validateSales(row, create, activeFields);
    }
  }

  private void validateCropRefs(RecordRow row, boolean create, Set<String> activeFields) {
    boolean cropChanged = create || activeFields.contains("cropId");
    boolean varietyChanged = create || activeFields.contains("varietyId");
    boolean seasonChanged = create || activeFields.contains("seasonId");
    boolean selectionChanged = cropChanged || varietyChanged || seasonChanged;
    if (!selectionChanged) return;
    if (row.getSeasonId() != null) {
      CropSeasonEntity season =
          masterDataMapper
              .findCropSeason(row.getFarmId(), row.getSeasonId())
              .orElseThrow(() -> new BusinessException(ErrorCode.CROP_SEASON_NOT_FOUND));
      if (seasonChanged && !CropSeasonEntity.STATUS_ACTIVE.equals(season.getStatus()))
        throw new BusinessException(ErrorCode.CROP_SEASON_NOT_FOUND);
      if (row.getCropId() == null) {
        if (!create && activeFields.contains("cropId"))
          throw validation("작기가 남아 있으면 작물을 비울 수 없습니다.");
        row.setCropId(season.getCropId());
        cropChanged = true;
      }
      if (!Objects.equals(row.getCropId(), season.getCropId()))
        throw validation("작기와 작물이 일치하지 않습니다.");
      if (row.getVarietyId() == null && season.getVarietyId() != null) {
        if (!create && activeFields.contains("varietyId"))
          throw validation("품종이 지정된 작기가 남아 있으면 품종을 비울 수 없습니다.");
        row.setVarietyId(season.getVarietyId());
        varietyChanged = true;
      }
      if (!Objects.equals(row.getVarietyId(), season.getVarietyId()))
        throw validation("작기와 품종이 일치하지 않습니다.");
    }
    if (row.getCropId() != null && cropChanged)
      requireRef(mapper.findCrop(row.getFarmId(), row.getCropId(), true), ErrorCode.CROP_NOT_FOUND);
    if (row.getVarietyId() != null) {
      if (row.getCropId() == null) throw validation("품종을 선택하려면 작물이 필요합니다.");
      if (varietyChanged) {
        requireRef(
            mapper.findVariety(row.getFarmId(), row.getCropId(), row.getVarietyId(), true),
            ErrorCode.CROP_VARIETY_NOT_FOUND);
      } else if (cropChanged) {
        // 품종 자체는 과거 비활성 참조를 허용하되 새 작물과의 소속 관계는 반드시 일치해야 한다.
        requireRef(
            mapper.findVariety(row.getFarmId(), row.getCropId(), row.getVarietyId(), false),
            ErrorCode.CROP_VARIETY_NOT_FOUND);
      }
    }
  }

  private void validateSales(RecordRow row, boolean create, Set<String> activeFields) {
    if (row.getCustomerId() == null) throw validation("거래처는 필수입니다.");
    if (create || activeFields.contains("customerId"))
      requireRef(
          mapper.findCustomer(row.getFarmId(), row.getCustomerId(), true),
          ErrorCode.CUSTOMER_NOT_FOUND);
    positiveRequired(row.getQuantity(), "판매 수량");
    nonNegative(row.getUnitPrice(), "단가");
    nonNegative(row.getFeeAmount(), "수수료");
    requiredText(row.getUnit(), 20, "단위");
    max(row.getItemName(), 150, "품목명");
    max(row.getMemo(), 2000, "메모");
    if (!Set.of("PENDING", "DONE").contains(row.getSettlementStatus()))
      throw validation("정산 상태는 PENDING 또는 DONE입니다.");
    decimal(row.getQuantity(), 12, 2, "판매 수량");
    decimal(row.getUnitPrice(), 14, 2, "단가");
    decimal(row.getGrossAmount(), 14, 2, "총금액");
    decimal(row.getFeeAmount(), 14, 2, "수수료");
    decimal(row.getNetAmount(), 14, 2, "순금액");
    if (row.getFeeAmount().compareTo(row.getGrossAmount()) > 0)
      throw validation("수수료는 총금액을 넘을 수 없습니다.");
    if (row.getNetAmount().signum() < 0 || row.getNetAmount().compareTo(row.getGrossAmount()) > 0)
      throw validation("순금액은 0 이상 총금액 이하여야 합니다.");
  }

  private Set<String> changedFkFields(RecordRow row, RecordMutationRequest r) {
    Set<String> changed = new HashSet<>();
    check(changed, "zoneId", r.has("zoneId"), row.getZoneId(), r.zoneId());
    check(changed, "cropId", r.has("cropId"), row.getCropId(), r.cropId());
    check(changed, "varietyId", r.has("varietyId"), row.getVarietyId(), r.varietyId());
    check(changed, "seasonId", r.has("seasonId"), row.getSeasonId(), r.seasonId());
    check(changed, "workTypeId", r.has("workTypeId"), row.getWorkTypeId(), r.workTypeId());
    check(changed, "customerId", r.has("customerId"), row.getCustomerId(), r.customerId());
    return changed;
  }

  private void check(Set<String> s, String n, boolean p, Object a, Object b) {
    if (p && !Objects.equals(a, b)) s.add(n);
  }

  private RecordResponse response(RecordRow row, FarmMembership membership) {
    hydrateReferences(row);
    boolean editable = canMutate(membership, row);
    LocalDate d = row.getRecordDate();
    BigDecimal calculated =
        row.getType() == RecordType.SALES
                && row.getGrossAmount() != null
                && row.getFeeAmount() != null
            ? money(row.getGrossAmount().subtract(row.getFeeAmount()))
            : null;
    RecordResponse.Actor confirmer =
        row.getFarmerConfirmedBy() == null
            ? null
            : actor(
                row.getFarmerConfirmedBy(),
                mapper
                    .findUser(row.getFarmerConfirmedBy())
                    .map(ReferenceRow::getName)
                    .orElse(null));
    return new RecordResponse(
        row.getType(),
        row.getId(),
        row.getFarmId(),
        row.getType() == RecordType.WORK ? d : null,
        row.getType() == RecordType.PEST_CONTROL ? d : null,
        row.getType() == RecordType.HARVEST ? d : null,
        row.getType() == RecordType.SALES ? d : null,
        ref(row.getZone()),
        ref(row.getCrop()),
        ref(row.getVariety()),
        ref(row.getSeason()),
        ref(row.getWorkType()),
        ref(row.getCustomer()),
        row.getWorkerCount(),
        row.getWorkHours(),
        row.getChemicalName(),
        row.getTargetPest(),
        row.getDilutionRatio(),
        row.getAmountValue(),
        row.getAmountUnit(),
        row.getPreharvestIntervalDays(),
        row.getGrade(),
        row.getQuantity(),
        row.getUnit(),
        row.getPackageUnit(),
        row.getItemName(),
        row.getUnitPrice(),
        row.getGrossAmount(),
        row.getFeeAmount(),
        calculated,
        row.getNetAmount(),
        YES.equals(row.getNetAmountOverriddenYn()),
        row.getSettlementStatus(),
        row.getMemo(),
        actor(row.getCreatedBy(), row.getCreatedByName()),
        row.getCreatedRole(),
        "FARM_CARE_MANAGER".equals(row.getCreatedRole()),
        row.getFarmerConfirmStatus(),
        row.getCareAssignmentId(),
        confirmer,
        row.getFarmerConfirmedAt(),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        row.getVersion(),
        editable,
        editable);
  }

  private FeedResponse feedResponse(FeedRow row, FarmMembership membership) {
    RecordRow permission = new RecordRow();
    permission.setFarmId(row.getFarmId());
    permission.setCreatedBy(row.getCreatedBy());
    permission.setCareAssignmentId(row.getCareAssignmentId());
    boolean editable = canMutate(membership, permission);
    return new FeedResponse(
        row.getType(),
        row.getId(),
        row.getRecordDate(),
        row.getZoneId() == null
            ? null
            : new RecordResponse.Reference(
                row.getZoneId(), row.getZoneName(), YES.equals(row.getZoneActiveYn())),
        row.getPrimaryLabel(),
        row.getPrimaryValue(),
        actor(row.getCreatedBy(), row.getCreatedByName()),
        row.getCreatedRole(),
        "FARM_CARE_MANAGER".equals(row.getCreatedRole()),
        row.getFarmerConfirmStatus(),
        row.getCreatedAt(),
        editable,
        editable);
  }

  private void hydrateReferences(RecordRow r) {
    if (r.getZoneId() != null)
      r.setZone(mapper.findZone(r.getFarmId(), r.getZoneId(), false).orElse(null));
    if (r.getCropId() != null)
      r.setCrop(mapper.findCrop(r.getFarmId(), r.getCropId(), false).orElse(null));
    if (r.getVarietyId() != null && r.getCropId() != null)
      r.setVariety(
          mapper.findVariety(r.getFarmId(), r.getCropId(), r.getVarietyId(), false).orElse(null));
    if (r.getSeasonId() != null)
      r.setSeason(mapper.findSeason(r.getFarmId(), r.getSeasonId(), false).orElse(null));
    if (r.getWorkTypeId() != null)
      r.setWorkType(mapper.findWorkType(r.getFarmId(), r.getWorkTypeId(), false).orElse(null));
    if (r.getCustomerId() != null)
      r.setCustomer(mapper.findCustomer(r.getFarmId(), r.getCustomerId(), false).orElse(null));
  }

  private int insert(RecordRow r) {
    return switch (r.getType()) {
      case WORK -> mapper.insertWork(r);
      case PEST_CONTROL -> mapper.insertPest(r);
      case HARVEST -> mapper.insertHarvest(r);
      case SALES -> mapper.insertSales(r);
    };
  }

  private int update(RecordRow r, Long v) {
    return switch (r.getType()) {
      case WORK -> mapper.updateWork(r, v);
      case PEST_CONTROL -> mapper.updatePest(r, v);
      case HARVEST -> mapper.updateHarvest(r, v);
      case SALES -> mapper.updateSales(r, v);
    };
  }

  private RecordRow get(RecordType t, Long f, Long id) {
    return mapper.findById(t, f, id).orElseThrow(() -> new BusinessException(error(t)));
  }

  private ErrorCode error(RecordType t) {
    return switch (t) {
      case WORK -> ErrorCode.WORK_LOG_NOT_FOUND;
      case PEST_CONTROL -> ErrorCode.PEST_CONTROL_LOG_NOT_FOUND;
      case HARVEST -> ErrorCode.HARVEST_LOG_NOT_FOUND;
      case SALES -> ErrorCode.SALES_LOG_NOT_FOUND;
    };
  }

  private void audit(RecordRow r, Long user, String action, String json) {
    mapper.insertAudit(
        r.getOrganizationId(),
        r.getFarmId(),
        user,
        action,
        r.getType().name(),
        r.getId(),
        json,
        LocalDateTime.now());
  }

  private void requireMutation(AccessContext context, RecordRow row) {
    if (!canMutate(context.membership(), row, context.assignment()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
  }

  private boolean canMutate(FarmMembership membership, RecordRow row) {
    CareAssignmentRow assignment = null;
    if ("FARM_CARE_MANAGER".equals(membership.role())
        && careAccessGuard != null
        && row.getFarmId() != null)
      assignment = careAccessGuard.findActive(membership.userId(), row.getFarmId()).orElse(null);
    return canMutate(membership, row, assignment);
  }

  private boolean canMutate(
      FarmMembership membership, RecordRow row, CareAssignmentRow assignment) {
    if (MANAGERS.contains(membership.role())) return true;
    if (FarmMemberEntity.ROLE_WORKER.equals(membership.role()))
      return Objects.equals(membership.userId(), row.getCreatedBy());
    // 재배정 후에는 과거 assignment가 만든 기록을 수정할 수 없다.
    return "FARM_CARE_MANAGER".equals(membership.role())
        && assignment != null
        && Objects.equals(membership.userId(), row.getCreatedBy())
        && Objects.equals(assignment.getId(), row.getCareAssignmentId());
  }

  private AccessContext access(Long userId, Long farmId) {
    FarmMembership membership;
    try {
      membership = accessGuard.requireFarmMember(userId, farmId);
    } catch (BusinessException ex) {
      if (ex.getErrorCode() != ErrorCode.FORBIDDEN || careAccessGuard == null) throw ex;
      CareAssignmentRow assignment = careAccessGuard.requireActive(userId, farmId);
      return new AccessContext(new FarmMembership(farmId, userId, "FARM_CARE_MANAGER"), assignment);
    }
    if ("FARM_CARE_MANAGER".equals(membership.role()))
      throw new BusinessException(ErrorCode.FORBIDDEN);
    return new AccessContext(membership, null);
  }

  private AccessContext accessForWrite(Long userId, Long farmId) {
    AccessContext context = access(userId, farmId);
    // 모든 자식 행과 care assignment보다 부모 농장을 먼저 잠근다.
    mutationGuard.lockActiveFarm(farmId);
    if ("FARM_CARE_MANAGER".equals(context.membership().role())) {
      CareAssignmentRow assignment = careAccessGuard.requireActiveForUpdate(userId, farmId);
      return new AccessContext(context.membership(), assignment);
    }
    FarmMembership current = accessGuard.requireFarmRoleForUpdate(
        userId, farmId, "FARM_OWNER", "FARM_MANAGER", "WORKER", "VIEWER");
    return new AccessContext(current, null);
  }

  @Transactional
  public RecordResponse farmerConfirmation(
      Long userId, Long farmId, RecordType type, Long id, FarmerConfirmationRequest request) {
    FarmMembership membership =
        accessGuard.requireFarmRole(userId, farmId, FarmMemberEntity.ROLE_FARM_OWNER);
    mutationGuard.lockActiveFarm(farmId);
    membership = accessGuard.requireFarmRoleForUpdate(
        userId, farmId, FarmMemberEntity.ROLE_FARM_OWNER);
    if (request == null
        || request.version() == null
        || !Set.of("CONFIRMED", "CHANGES_REQUESTED").contains(request.status()))
      throw validation("status는 CONFIRMED 또는 CHANGES_REQUESTED이며 version은 필수입니다.");
    RecordRow row = get(type, farmId, id);
    if (!"FARM_CARE_MANAGER".equals(row.getCreatedRole()))
      throw validation("매니저 입력 기록만 농가 확인할 수 있습니다.");
    if (mapper.updateFarmerConfirmation(
            type, farmId, id, request.status(), request.version(), userId, LocalDateTime.now())
        != 1) throw conflict();
    audit(
        row,
        userId,
        "FARMER_CONFIRMATION",
        "{\"status\":\"" + request.status() + "\",\"version\":" + request.version() + "}");
    return response(get(type, farmId, id), membership);
  }

  private record AccessContext(FarmMembership membership, CareAssignmentRow assignment) {}

  private void requireRole(FarmMembership m, Set<String> roles) {
    if (!roles.contains(m.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
  }

  private void requireRef(Optional<ReferenceRow> ref, ErrorCode code) {
    if (ref.isEmpty()) throw new BusinessException(code);
  }

  private void validateMutationFields(
      RecordType type, RecordMutationRequest request, boolean create) {
    if (request == null) throw validation("요청 본문은 필수입니다.");
    Set<String> allowed = new HashSet<>(MUTATION_ALLOWED.get(type));
    allowed.add(create ? "clientRequestId" : "version");
    if (!allowed.containsAll(request.presentFields()))
      throw validation("이 기록 유형 또는 작업에서 지원하지 않는 필드가 포함되었습니다.");
  }

  private void validateBulkChanges(RecordType t, RecordMutationRequest r) {
    if (r == null || r.presentFields().isEmpty()) throw validation("일괄 수정 changes는 비어 있을 수 없습니다.");
    if (!BULK_ALLOWED.get(t).containsAll(r.presentFields()))
      throw validation("일괄 수정할 수 없는 필드가 포함되었습니다.");
  }

  private Set<String> allFkFields(RecordType t) {
    return t == RecordType.SALES
        ? Set.of("customerId")
        : Set.of("zoneId", "cropId", "varietyId", "seasonId", "workTypeId");
  }

  private void validatePage(int p, int s) {
    if (p < 0 || s < 1 || s > 100 || (long) p * s > Integer.MAX_VALUE)
      throw validation("page는 0 이상, size는 1~100이며 처리 가능한 범위여야 합니다.");
  }

  private void validateDateRange(LocalDate f, LocalDate t) {
    if (f != null && t != null && f.isAfter(t)) throw validation("시작일은 종료일보다 늦을 수 없습니다.");
  }

  private void validateDomainFilters(
      RecordType t, Long z, Long c, Long v, Long s, Long w, Long customer, String status) {
    if (t == RecordType.SALES && (z != null || c != null || v != null || s != null || w != null))
      throw validation("판매 기록에 지원하지 않는 필터입니다.");
    if (t != RecordType.SALES && (customer != null || status != null))
      throw validation("이 기록 유형에 지원하지 않는 판매 필터입니다.");
    if (t != RecordType.WORK && w != null) throw validation("작업유형 필터는 작업 기록에서만 사용합니다.");
    if (t == RecordType.SALES
        && status != null
        && (!StringUtils.hasText(status) || !Set.of("PENDING", "DONE").contains(status)))
      throw validation("정산 상태는 PENDING 또는 DONE입니다.");
  }

  private SortSpec sort(RecordType t, String raw) {
    String field = t.defaultSort(), dir = "DESC";
    if (StringUtils.hasText(raw)) {
      String[] p = raw.split(",", -1);
      if (p.length != 2
          || !t.allowsSort(p[0])
          || !(p[1].equalsIgnoreCase("asc") || p[1].equalsIgnoreCase("desc")))
        throw validation("지원하지 않는 정렬입니다.");
      field = p[0];
      dir = p[1].toUpperCase();
    }
    return new SortSpec(column(t, field), dir);
  }

  private SortSpec feedSort(String raw) {
    String f = "recordDate", d = "DESC";
    if (StringUtils.hasText(raw)) {
      String[] p = raw.split(",", -1);
      if (p.length != 2
          || !Set.of("recordDate", "createdAt").contains(p[0])
          || !(p[1].equalsIgnoreCase("asc") || p[1].equalsIgnoreCase("desc")))
        throw validation("지원하지 않는 정렬입니다.");
      f = p[0];
      d = p[1].toUpperCase();
    }
    return new SortSpec(f.equals("recordDate") ? "q.record_date" : "q.created_at", d);
  }

  private String column(RecordType t, String f) {
    return switch (f) {
      case "workDate" -> "l.work_date";
      case "applyDate" -> "l.apply_date";
      case "harvestDate" -> "l.harvest_date";
      case "salesDate" -> "l.sales_date";
      case "quantity" -> "l.quantity";
      case "unitPrice" -> "l.unit_price";
      case "grossAmount" -> "l.gross_amount";
      case "netAmount" -> "l.net_amount";
      case "updatedAt" -> "l.updated_at";
      default -> "l.created_at";
    };
  }

  private record SortSpec(String column, String direction) {}

  private void validateClientRequestId(String id) {
    if (id == null) return;
    try {
      UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw validation("clientRequestId는 UUID 형식이어야 합니다.");
    }
  }

  private BusinessException validation(String m) {
    return new BusinessException(ErrorCode.VALIDATION_FAILED, m);
  }

  private BusinessException conflict() {
    return new BusinessException(ErrorCode.CONFLICT, "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해주세요.");
  }

  private void positive(BigDecimal v, String n) {
    positive(v, 8, 2, n);
  }

  private void positive(BigDecimal v, int precision, int scale, String n) {
    if (v != null) {
      if (v.signum() <= 0) throw validation(n + "은(는) 양수여야 합니다.");
      decimal(v, precision, scale, n);
    }
  }

  private void positiveRequired(BigDecimal v, String n) {
    if (v == null || v.signum() <= 0) throw validation(n + "은(는) 양수여야 합니다.");
  }

  private void nonNegative(BigDecimal v, String n) {
    if (v == null || v.signum() < 0) throw validation(n + "은(는) 0 이상이어야 합니다.");
  }

  private void decimal(BigDecimal v, int precision, int scale, String n) {
    if (v != null && (v.scale() > scale || v.precision() > precision))
      throw validation(n + "의 자릿수를 확인해주세요.");
  }

  private void requiredText(String v, int max, String n) {
    if (!StringUtils.hasText(v)) throw validation(n + "은(는) 필수입니다.");
    max(v, max, n);
  }

  private void max(String v, int max, String n) {
    if (v != null && v.length() > max) throw validation(n + "은(는) " + max + "자 이하입니다.");
  }

  private String trimNullable(String v) {
    return StringUtils.hasText(v) ? v.trim() : null;
  }

  private String defaultText(String v, String d) {
    return StringUtils.hasText(v) ? v.trim() : d;
  }

  private BigDecimal money(BigDecimal v) {
    return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
  }

  private RecordResponse.Reference ref(ReferenceRow r) {
    return r == null
        ? null
        : new RecordResponse.Reference(r.getId(), r.getName(), YES.equals(r.getActiveYn()));
  }

  private RecordResponse.Actor actor(Long id, String name) {
    return id == null ? null : new RecordResponse.Actor(id, name);
  }
}
