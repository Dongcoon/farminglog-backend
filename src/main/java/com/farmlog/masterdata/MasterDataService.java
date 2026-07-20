package com.farmlog.masterdata;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.entity.FarmMemberEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.masterdata.dto.*;
import com.farmlog.masterdata.entity.CatalogEntity;
import com.farmlog.masterdata.entity.CropSeasonEntity;
import com.farmlog.masterdata.entity.FarmCatalogRow;
import com.farmlog.masterdata.entity.LocalMasterDataRow;
import com.farmlog.masterdata.mapper.MasterDataMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Phase 3 기준정보 서비스. 모든 농장 경로는 서비스 진입 즉시 멤버십을 검사하며, 변경 작업은
 * FARM_OWNER/FARM_MANAGER로 한정한다. 전역 crop 카탈로그에는 INSERT만 하고 UPDATE는 하지 않는다.
 */
@Service
public class MasterDataService {
    private static final String YES = "Y";
    private static final String NO = "N";
    private static final String TEMPLATE_STRAWBERRY = "STRAWBERRY";
    private static final List<String> STRAWBERRY_VARIETIES = List.of("설향", "금실", "킹스베리");
    private static final List<String> STRAWBERRY_WORK_TYPES = List.of("정식", "유인", "적화·적과", "관수", "포장");

    private final MasterDataMapper mapper;
    private final FarmMapper farmMapper;
    private final FarmAccessGuard accessGuard;
    private final FarmMutationGuard mutationGuard;

    @Autowired
    public MasterDataService(MasterDataMapper mapper, FarmMapper farmMapper, FarmAccessGuard accessGuard,
                             FarmMutationGuard mutationGuard) {
        this.mapper = mapper;
        this.farmMapper = farmMapper;
        this.accessGuard = accessGuard;
        this.mutationGuard = mutationGuard;
    }

    public MasterDataService(MasterDataMapper mapper, FarmMapper farmMapper, FarmAccessGuard accessGuard) {
        this(mapper, farmMapper, accessGuard, new FarmMutationGuard(farmMapper));
    }

    public List<CatalogResponse> listCrops(Long userId, Long farmId, boolean includeInactive) {
        requireMember(userId, farmId);
        return mapper.findFarmCrops(farmId, includeInactive).stream().map(this::catalogResponse).toList();
    }

    @Transactional
    public CatalogResponse createCrop(Long userId, Long farmId, CatalogCreateRequest request) {
        requireManager(userId, farmId);
        String name = request.name().trim();
        CatalogEntity catalog = ensureCrop(name);
        FarmCatalogRow existing = mapper.findFarmCrop(farmId, catalog.getId()).orElse(null);
        int order = request.displayOrder() == null ? 0 : request.displayOrder();
        if (existing != null && existing.getDisplayOrder() == order && YES.equals(existing.getActiveYn())) {
            return catalogResponse(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            mapper.insertFarmCropIfAbsent(farmId, catalog.getId(), order, userId, now);
        }
        mapper.updateFarmCrop(farmId, catalog.getId(), order, YES, userId, now);
        structureChanged(userId, farmId);
        return getCrop(farmId, catalog.getId());
    }

    @Transactional
    public CatalogResponse updateCrop(Long userId, Long farmId, Long cropId, CatalogUpdateRequest request) {
        requireManager(userId, farmId);
        FarmCatalogRow current = getCropRow(farmId, cropId);
        int order = request.displayOrder() == null ? current.getDisplayOrder() : request.displayOrder();
        String newName = request.name() == null ? current.getName() : request.name().trim();
        if (newName.equals(current.getName())) {
            if (order == current.getDisplayOrder()) return catalogResponse(current);
            mapper.updateFarmCrop(farmId, cropId, order, current.getActiveYn(), userId, LocalDateTime.now());
            structureChanged(userId, farmId);
            return getCrop(farmId, cropId);
        }

        CatalogEntity target = ensureCrop(newName);
        FarmCatalogRow targetLink = mapper.findFarmCrop(farmId, target.getId()).orElse(null);
        if (targetLink != null && YES.equals(targetLink.getActiveYn())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 이 농장에 등록된 작물 이름입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.insertFarmCropIfAbsent(farmId, target.getId(), order, userId, now);
        mapper.updateFarmCrop(farmId, target.getId(), order, current.getActiveYn(), userId, now);

        // 이름 변경은 새 카탈로그로 연결을 바꾸며, 기존 품종도 새 crop 아래 복제 연결한다.
        // 진행 중인 작기는 같은 ID를 유지한 채 새 카탈로그로 이관하고, 완료 작기와 과거 기록은 건드리지 않는다.
        for (FarmCatalogRow oldVariety : mapper.findFarmVarieties(farmId, cropId, true)) {
            CatalogEntity newVariety = ensureVariety(target.getId(), oldVariety.getName());
            mapper.insertFarmVarietyIfAbsent(farmId, target.getId(), newVariety.getId(),
                    oldVariety.getDisplayOrder(), userId, now);
            mapper.updateFarmVariety(farmId, newVariety.getId(), target.getId(), oldVariety.getDisplayOrder(),
                    oldVariety.getActiveYn(), userId, now);
            mapper.migrateActiveSeasonsForCropVariety(farmId, cropId, oldVariety.getVarietyId(),
                    target.getId(), newVariety.getId(), userId, now);
            mapper.updateFarmVariety(farmId, oldVariety.getVarietyId(), cropId, oldVariety.getDisplayOrder(), NO, userId, now);
        }
        mapper.migrateActiveSeasonsWithoutVariety(farmId, cropId, target.getId(), userId, now);
        mapper.updateFarmCrop(farmId, cropId, current.getDisplayOrder(), NO, userId, now);
        structureChanged(userId, farmId);
        return getCrop(farmId, target.getId());
    }

    @Transactional
    public CatalogResponse deactivateCrop(Long userId, Long farmId, Long cropId) {
        requireManager(userId, farmId);
        FarmCatalogRow row = getCropRow(farmId, cropId);
        if (NO.equals(row.getActiveYn())) return catalogResponse(row);
        LocalDateTime now = LocalDateTime.now();
        mapper.updateFarmCrop(farmId, cropId, row.getDisplayOrder(), NO, userId, now);
        // 신규 기록 선택 목록에서 자식 품종도 숨기되, 연결/카탈로그 행은 삭제하지 않는다.
        for (FarmCatalogRow variety : mapper.findFarmVarieties(farmId, cropId, true)) {
            mapper.updateFarmVariety(farmId, variety.getVarietyId(), cropId, variety.getDisplayOrder(), NO, userId, now);
        }
        structureChanged(userId, farmId);
        return getCrop(farmId, cropId);
    }

    public List<CatalogResponse> listVarieties(Long userId, Long farmId, Long cropId, boolean includeInactive) {
        requireMember(userId, farmId);
        getCropRow(farmId, cropId);
        return mapper.findFarmVarieties(farmId, cropId, includeInactive).stream().map(this::catalogResponse).toList();
    }

    @Transactional
    public CatalogResponse createVariety(Long userId, Long farmId, Long cropId, CatalogCreateRequest request) {
        requireManager(userId, farmId);
        getCropRow(farmId, cropId);
        CatalogEntity catalog = ensureVariety(cropId, request.name().trim());
        int order = request.displayOrder() == null ? 0 : request.displayOrder();
        FarmCatalogRow existing = mapper.findFarmVariety(farmId, cropId, catalog.getId()).orElse(null);
        if (existing != null && existing.getDisplayOrder() == order && YES.equals(existing.getActiveYn())) {
            return catalogResponse(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.insertFarmVarietyIfAbsent(farmId, cropId, catalog.getId(), order, userId, now);
        mapper.updateFarmVariety(farmId, catalog.getId(), cropId, order, YES, userId, now);
        structureChanged(userId, farmId);
        return getVariety(farmId, cropId, catalog.getId());
    }

    @Transactional
    public CatalogResponse updateVariety(Long userId, Long farmId, Long cropId, Long varietyId,
                                         CatalogUpdateRequest request) {
        requireManager(userId, farmId);
        FarmCatalogRow current = getVarietyRow(farmId, cropId, varietyId);
        int order = request.displayOrder() == null ? current.getDisplayOrder() : request.displayOrder();
        String name = request.name() == null ? current.getName() : request.name().trim();
        if (name.equals(current.getName())) {
            if (order == current.getDisplayOrder()) return catalogResponse(current);
            mapper.updateFarmVariety(farmId, varietyId, cropId, order, current.getActiveYn(), userId, LocalDateTime.now());
            structureChanged(userId, farmId);
            return getVariety(farmId, cropId, varietyId);
        }
        CatalogEntity target = ensureVariety(cropId, name);
        FarmCatalogRow targetLink = mapper.findFarmVariety(farmId, cropId, target.getId()).orElse(null);
        if (targetLink != null && YES.equals(targetLink.getActiveYn())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 이 농장에 등록된 품종 이름입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.insertFarmVarietyIfAbsent(farmId, cropId, target.getId(), order, userId, now);
        mapper.updateFarmVariety(farmId, target.getId(), cropId, order, current.getActiveYn(), userId, now);
        mapper.migrateActiveSeasonsForVariety(farmId, cropId, varietyId, target.getId(), userId, now);
        mapper.updateFarmVariety(farmId, varietyId, cropId, current.getDisplayOrder(), NO, userId, now);
        structureChanged(userId, farmId);
        return getVariety(farmId, cropId, target.getId());
    }

    @Transactional
    public CatalogResponse deactivateVariety(Long userId, Long farmId, Long cropId, Long varietyId) {
        requireManager(userId, farmId);
        FarmCatalogRow row = getVarietyRow(farmId, cropId, varietyId);
        if (NO.equals(row.getActiveYn())) return catalogResponse(row);
        mapper.updateFarmVariety(farmId, varietyId, cropId, row.getDisplayOrder(), NO, userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return getVariety(farmId, cropId, varietyId);
    }

    public List<CropSeasonResponse> listCropSeasons(Long userId, Long farmId, boolean includeCompleted) {
        requireMember(userId, farmId);
        return mapper.findCropSeasons(farmId, includeCompleted).stream().map(this::seasonResponse).toList();
    }

    @Transactional
    public CropSeasonResponse createCropSeason(Long userId, Long farmId, CropSeasonCreateRequest request) {
        requireManager(userId, farmId);
        FarmEntity farm = getFarm(farmId);
        validateCropSelection(farmId, request.cropId(), request.varietyId());
        validateDates(request.startDate(), request.endDate());
        CropSeasonEntity season = new CropSeasonEntity();
        season.setOrganizationId(farm.getOrganizationId());
        season.setFarmId(farmId);
        season.setCropId(request.cropId());
        season.setVarietyId(request.varietyId());
        season.setName(request.name().trim());
        season.setStartDate(request.startDate());
        season.setEndDate(request.endDate());
        season.setStatus(CropSeasonEntity.STATUS_ACTIVE);
        mapper.insertCropSeason(season, userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return seasonResponse(season);
    }

    @Transactional
    public CropSeasonResponse updateCropSeason(Long userId, Long farmId, Long seasonId, CropSeasonUpdateRequest request) {
        requireManager(userId, farmId);
        CropSeasonEntity season = getSeason(farmId, seasonId);
        if (CropSeasonEntity.STATUS_COMPLETED.equals(season.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "완료된 작기는 수정할 수 없습니다.");
        }
        boolean cropChanged = request.cropId() != null && !Objects.equals(season.getCropId(), request.cropId());
        boolean varietyChanged = request.hasVarietyId() && !Objects.equals(season.getVarietyId(), request.varietyId());
        if (request.cropId() != null) season.setCropId(request.cropId());
        if (request.hasVarietyId()) season.setVarietyId(request.varietyId());
        if (request.name() != null) season.setName(request.name().trim());
        if (request.startDate() != null) season.setStartDate(request.startDate());
        if (request.hasEndDate()) season.setEndDate(request.endDate());
        // 이름/기간만 수정하는 경우에는 과거에 선택한 비활성 연결을 보존한다. 실제 선택 변경만
        // 신규 기록과 동일한 active-only 규칙으로 검증해 COW/비활성화 이후에도 작기를 정정할 수 있다.
        if (cropChanged || varietyChanged) {
            validateCropSelection(farmId, season.getCropId(), season.getVarietyId());
        }
        validateDates(season.getStartDate(), season.getEndDate());
        mapper.updateCropSeason(season, userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return seasonResponse(season);
    }

    @Transactional
    public CropSeasonResponse completeCropSeason(Long userId, Long farmId, Long seasonId) {
        requireManager(userId, farmId);
        CropSeasonEntity season = getSeason(farmId, seasonId);
        if (CropSeasonEntity.STATUS_COMPLETED.equals(season.getStatus())) return seasonResponse(season);
        LocalDate endDate = season.getEndDate() == null ? LocalDate.now() : season.getEndDate();
        validateDates(season.getStartDate(), endDate);
        mapper.completeCropSeason(farmId, seasonId, endDate, userId, LocalDateTime.now());
        season.setEndDate(endDate);
        season.setStatus(CropSeasonEntity.STATUS_COMPLETED);
        structureChanged(userId, farmId);
        return seasonResponse(season);
    }

    @Transactional
    public void deleteCropSeason(Long userId, Long farmId, Long seasonId) {
        requireManager(userId, farmId);
        getSeason(farmId, seasonId);
        // crop_season 행은 작업/수확 등 과거 기록이 참조하므로 물리 삭제하지 않고 관리 목록에서만 숨긴다.
        mapper.softDeleteCropSeason(farmId, seasonId, userId, LocalDateTime.now());
        structureChanged(userId, farmId);
    }

    public List<WorkTypeResponse> listWorkTypes(Long userId, Long farmId, boolean includeInactive) {
        requireMember(userId, farmId);
        return mapper.findWorkTypes(farmId, includeInactive).stream().map(this::workTypeResponse).toList();
    }

    @Transactional
    public WorkTypeResponse createWorkType(Long userId, Long farmId, WorkTypeCreateRequest request) {
        requireManager(userId, farmId);
        FarmEntity farm = getFarm(farmId);
        String name = request.name().trim();
        int order = request.displayOrder() == null ? 0 : request.displayOrder();
        LocalDateTime now = LocalDateTime.now();
        int created = mapper.insertWorkTypeIfAbsent(farm.getOrganizationId(), farmId, name, order, userId, now);
        LocalMasterDataRow row = mapper.findWorkTypeByName(farmId, name).orElseThrow();
        if (created == 0 && YES.equals(row.getActiveYn()) && row.getDisplayOrder() == order) return workTypeResponse(row);
        mapper.updateWorkType(farmId, row.getId(), name, order, YES, userId, now);
        structureChanged(userId, farmId);
        return workTypeResponse(mapper.findWorkType(farmId, row.getId()).orElseThrow());
    }

    @Transactional
    public WorkTypeResponse updateWorkType(Long userId, Long farmId, Long id, WorkTypeUpdateRequest request) {
        requireManager(userId, farmId);
        LocalMasterDataRow row = getWorkType(farmId, id);
        String name = request.name() == null ? row.getName() : request.name().trim();
        int order = request.displayOrder() == null ? row.getDisplayOrder() : request.displayOrder();
        if (name.equals(row.getName()) && order == row.getDisplayOrder()) return workTypeResponse(row);
        assertLocalNameAvailable(mapper.findWorkTypeByName(farmId, name).orElse(null), id);
        mapper.updateWorkType(farmId, id, name, order, row.getActiveYn(), userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return workTypeResponse(mapper.findWorkType(farmId, id).orElseThrow());
    }

    @Transactional
    public WorkTypeResponse deactivateWorkType(Long userId, Long farmId, Long id) {
        requireManager(userId, farmId);
        LocalMasterDataRow row = getWorkType(farmId, id);
        if (NO.equals(row.getActiveYn())) return workTypeResponse(row);
        mapper.updateWorkType(farmId, id, row.getName(), row.getDisplayOrder(), NO, userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return workTypeResponse(mapper.findWorkType(farmId, id).orElseThrow());
    }

    public List<CustomerResponse> listCustomers(Long userId, Long farmId, boolean includeInactive) {
        requireMember(userId, farmId);
        return mapper.findCustomers(farmId, includeInactive).stream().map(this::customerResponse).toList();
    }

    @Transactional
    public CustomerResponse createCustomer(Long userId, Long farmId, CustomerCreateRequest request) {
        requireManager(userId, farmId);
        FarmEntity farm = getFarm(farmId);
        String name = request.name().trim();
        assertLocalNameAvailable(mapper.findCustomerByName(farmId, name).orElse(null), null);
        mapper.insertCustomer(farm.getOrganizationId(), farmId, name, defaultText(request.customerType(), "OTHER"),
                request.phone(), request.memo(), userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return customerResponse(mapper.findCustomerByName(farmId, name).orElseThrow());
    }

    @Transactional
    public CustomerResponse updateCustomer(Long userId, Long farmId, Long id, CustomerUpdateRequest request) {
        requireManager(userId, farmId);
        LocalMasterDataRow row = getCustomer(farmId, id);
        String name = request.name() == null ? row.getName() : request.name().trim();
        assertLocalNameAvailable(mapper.findCustomerByName(farmId, name).orElse(null), id);
        String type = defaultText(request.customerType(), row.getType());
        String phone = request.hasPhone() ? request.phone() : row.getPhone();
        String memo = request.hasMemo() ? request.memo() : row.getMemo();
        if (name.equals(row.getName()) && Objects.equals(type, row.getType())
                && Objects.equals(phone, row.getPhone()) && Objects.equals(memo, row.getMemo())) return customerResponse(row);
        mapper.updateCustomer(farmId, id, name, type, phone, memo,
                row.getActiveYn(), userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return customerResponse(mapper.findCustomer(farmId, id).orElseThrow());
    }

    @Transactional
    public CustomerResponse deactivateCustomer(Long userId, Long farmId, Long id) {
        requireManager(userId, farmId);
        LocalMasterDataRow row = getCustomer(farmId, id);
        if (NO.equals(row.getActiveYn())) return customerResponse(row);
        mapper.updateCustomer(farmId, id, row.getName(), row.getType(), row.getPhone(), row.getMemo(), NO,
                userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return customerResponse(mapper.findCustomer(farmId, id).orElseThrow());
    }

    public List<MaterialResponse> listMaterials(Long userId, Long farmId, boolean includeInactive) {
        requireMember(userId, farmId);
        return mapper.findMaterials(farmId, includeInactive).stream().map(this::materialResponse).toList();
    }

    @Transactional
    public MaterialResponse createMaterial(Long userId, Long farmId, MaterialCreateRequest request) {
        requireManager(userId, farmId);
        FarmEntity farm = getFarm(farmId);
        String name = request.name().trim();
        assertLocalNameAvailable(mapper.findMaterialByName(farmId, name).orElse(null), null);
        mapper.insertMaterial(farm.getOrganizationId(), farmId, name, defaultText(request.materialType(), "ETC"),
                request.unit(), request.memo(), userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return materialResponse(mapper.findMaterialByName(farmId, name).orElseThrow());
    }

    @Transactional
    public MaterialResponse updateMaterial(Long userId, Long farmId, Long id, MaterialUpdateRequest request) {
        requireManager(userId, farmId);
        LocalMasterDataRow row = getMaterial(farmId, id);
        String name = request.name() == null ? row.getName() : request.name().trim();
        assertLocalNameAvailable(mapper.findMaterialByName(farmId, name).orElse(null), id);
        String type = defaultText(request.materialType(), row.getType());
        String unit = request.hasUnit() ? request.unit() : row.getUnit();
        String memo = request.hasMemo() ? request.memo() : row.getMemo();
        if (name.equals(row.getName()) && Objects.equals(type, row.getType())
                && Objects.equals(unit, row.getUnit()) && Objects.equals(memo, row.getMemo())) return materialResponse(row);
        mapper.updateMaterial(farmId, id, name, type, unit, memo,
                row.getActiveYn(), userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return materialResponse(mapper.findMaterial(farmId, id).orElseThrow());
    }

    @Transactional
    public MaterialResponse deactivateMaterial(Long userId, Long farmId, Long id) {
        requireManager(userId, farmId);
        LocalMasterDataRow row = getMaterial(farmId, id);
        if (NO.equals(row.getActiveYn())) return materialResponse(row);
        mapper.updateMaterial(farmId, id, row.getName(), row.getType(), row.getUnit(), row.getMemo(), NO,
                userId, LocalDateTime.now());
        structureChanged(userId, farmId);
        return materialResponse(mapper.findMaterial(farmId, id).orElseThrow());
    }

    public TemplatePreviewResponse previewStrawberryTemplate() {
        return new TemplatePreviewResponse(TEMPLATE_STRAWBERRY,
                new TemplatePreviewResponse.CropTemplate("딸기", STRAWBERRY_VARIETIES), STRAWBERRY_WORK_TYPES);
    }

    @Transactional
    public TemplateApplyResponse applyStrawberryTemplate(Long userId, Long farmId) {
        requireManager(userId, farmId);
        FarmEntity farm = getFarm(farmId);
        LocalDateTime now = LocalDateTime.now();
        int createdCrops = 0, createdVarieties = 0, createdWorkTypes = 0;
        boolean changed = false;

        CatalogEntity crop = ensureCrop("딸기");
        FarmCatalogRow cropBefore = mapper.findFarmCrop(farmId, crop.getId()).orElse(null);
        changed |= cropBefore == null || !YES.equals(cropBefore.getActiveYn()) || cropBefore.getDisplayOrder() != 0;
        createdCrops += mapper.insertFarmCropIfAbsent(farmId, crop.getId(), 0, userId, now);
        mapper.updateFarmCrop(farmId, crop.getId(), 0, YES, userId, now);
        for (int i = 0; i < STRAWBERRY_VARIETIES.size(); i++) {
            CatalogEntity variety = ensureVariety(crop.getId(), STRAWBERRY_VARIETIES.get(i));
            FarmCatalogRow varietyBefore = mapper.findFarmVariety(farmId, crop.getId(), variety.getId()).orElse(null);
            changed |= varietyBefore == null || !YES.equals(varietyBefore.getActiveYn())
                    || varietyBefore.getDisplayOrder() != i;
            createdVarieties += mapper.insertFarmVarietyIfAbsent(
                    farmId, crop.getId(), variety.getId(), i, userId, now);
            mapper.updateFarmVariety(farmId, variety.getId(), crop.getId(), i, YES, userId, now);
        }
        for (int i = 0; i < STRAWBERRY_WORK_TYPES.size(); i++) {
            String name = STRAWBERRY_WORK_TYPES.get(i);
            LocalMasterDataRow workTypeBefore = mapper.findWorkTypeByName(farmId, name).orElse(null);
            changed |= workTypeBefore == null || !YES.equals(workTypeBefore.getActiveYn())
                    || workTypeBefore.getDisplayOrder() != i;
            createdWorkTypes += mapper.insertWorkTypeIfAbsent(
                    farm.getOrganizationId(), farmId, name, i, userId, now);
            LocalMasterDataRow saved = mapper.findWorkTypeByName(farmId, name).orElseThrow();
            mapper.updateWorkType(farmId, saved.getId(), name, i, YES, userId, now);
        }
        if (changed) structureChanged(userId, farmId);
        return new TemplateApplyResponse(TEMPLATE_STRAWBERRY,
                new TemplateApplyResponse.Counts(createdCrops, createdVarieties, createdWorkTypes),
                new TemplateApplyResponse.Counts(1 - createdCrops, STRAWBERRY_VARIETIES.size() - createdVarieties,
                        STRAWBERRY_WORK_TYPES.size() - createdWorkTypes));
    }

    private CatalogEntity ensureCrop(String name) {
        mapper.insertCropIfAbsent(name);
        return mapper.findCropByName(name).orElseThrow();
    }

    private CatalogEntity ensureVariety(Long cropId, String name) {
        mapper.insertVarietyIfAbsent(cropId, name);
        return mapper.findVarietyByName(cropId, name).orElseThrow();
    }

    private void validateCropSelection(Long farmId, Long cropId, Long varietyId) {
        FarmCatalogRow crop = getCropRow(farmId, cropId);
        if (!YES.equals(crop.getActiveYn())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "비활성 작물은 새 작기에 사용할 수 없습니다.");
        }
        if (varietyId != null) {
            FarmCatalogRow variety = getVarietyRow(farmId, cropId, varietyId);
            if (!YES.equals(variety.getActiveYn())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "비활성 품종은 새 작기에 사용할 수 없습니다.");
            }
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private void requireMember(Long userId, Long farmId) { accessGuard.requireFarmMember(userId, farmId); }
    private void requireManager(Long userId, Long farmId) {
        accessGuard.requireFarmRole(userId, farmId, FarmMemberEntity.ROLE_FARM_OWNER, FarmMemberEntity.ROLE_FARM_MANAGER);
        // 구조 변경은 organization -> farm -> member -> master 순서로 직렬화한다.
        mutationGuard.lockActiveStructureFarm(farmId);
        accessGuard.requireFarmRoleForUpdate(
                userId, farmId, FarmMemberEntity.ROLE_FARM_OWNER, FarmMemberEntity.ROLE_FARM_MANAGER);
    }
    private void structureChanged(Long userId, Long farmId) {
        mutationGuard.incrementStructureVersion(farmId, userId);
    }
    private FarmEntity getFarm(Long farmId) {
        return farmMapper.findById(farmId).orElseThrow(() -> new BusinessException(ErrorCode.FARM_NOT_FOUND));
    }
    private FarmCatalogRow getCropRow(Long farmId, Long cropId) {
        return mapper.findFarmCrop(farmId, cropId).orElseThrow(() -> new BusinessException(ErrorCode.CROP_NOT_FOUND));
    }
    private FarmCatalogRow getVarietyRow(Long farmId, Long cropId, Long varietyId) {
        return mapper.findFarmVariety(farmId, cropId, varietyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CROP_VARIETY_NOT_FOUND));
    }
    private CropSeasonEntity getSeason(Long farmId, Long id) {
        return mapper.findCropSeason(farmId, id).orElseThrow(() -> new BusinessException(ErrorCode.CROP_SEASON_NOT_FOUND));
    }
    private LocalMasterDataRow getWorkType(Long farmId, Long id) {
        return mapper.findWorkType(farmId, id).orElseThrow(() -> new BusinessException(ErrorCode.WORK_TYPE_NOT_FOUND));
    }
    private LocalMasterDataRow getCustomer(Long farmId, Long id) {
        return mapper.findCustomer(farmId, id).orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
    }
    private LocalMasterDataRow getMaterial(Long farmId, Long id) {
        return mapper.findMaterial(farmId, id).orElseThrow(() -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND));
    }
    private CatalogResponse getCrop(Long farmId, Long id) { return catalogResponse(getCropRow(farmId, id)); }
    private CatalogResponse getVariety(Long farmId, Long cropId, Long id) { return catalogResponse(getVarietyRow(farmId, cropId, id)); }
    private CatalogResponse catalogResponse(FarmCatalogRow row) {
        Long id = row.getVarietyId() == null ? row.getCropId() : row.getVarietyId();
        return new CatalogResponse(id, row.getName(), row.getDisplayOrder(), YES.equals(row.getActiveYn()));
    }
    private CropSeasonResponse seasonResponse(CropSeasonEntity row) {
        return new CropSeasonResponse(row.getId(), row.getCropId(), row.getVarietyId(), row.getName(),
                row.getStartDate(), row.getEndDate(), row.getStatus());
    }
    private WorkTypeResponse workTypeResponse(LocalMasterDataRow row) {
        return new WorkTypeResponse(row.getId(), row.getName(), row.getDisplayOrder(), YES.equals(row.getActiveYn()));
    }
    private CustomerResponse customerResponse(LocalMasterDataRow row) {
        return new CustomerResponse(row.getId(), row.getName(), row.getType(), row.getPhone(), row.getMemo(), YES.equals(row.getActiveYn()));
    }
    private MaterialResponse materialResponse(LocalMasterDataRow row) {
        return new MaterialResponse(row.getId(), row.getName(), row.getType(), row.getUnit(), row.getMemo(), YES.equals(row.getActiveYn()));
    }
    private void assertLocalNameAvailable(LocalMasterDataRow sameName, Long currentId) {
        if (sameName != null && !Objects.equals(sameName.getId(), currentId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "같은 이름의 기준정보가 이미 있습니다.");
        }
    }
    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
