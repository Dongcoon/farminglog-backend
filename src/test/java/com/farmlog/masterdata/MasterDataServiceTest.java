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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MasterDataServiceTest {
    private static final Long USER = 7L;
    private static final Long FARM = 11L;
    private MasterDataMapper mapper;
    private FarmMapper farmMapper;
    private FarmAccessGuard guard;
    private FarmMutationGuard mutationGuard;
    private MasterDataService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MasterDataMapper.class);
        farmMapper = mock(FarmMapper.class);
        guard = mock(FarmAccessGuard.class);
        mutationGuard = mock(FarmMutationGuard.class);
        service = new MasterDataService(mapper, farmMapper, guard, mutationGuard);
        FarmEntity farm = FarmEntity.builder().id(FARM).organizationId(3L).build();
        when(farmMapper.findById(FARM)).thenReturn(Optional.of(farm));
    }

    @Test
    void listUsesMemberGuardAndDefaultsCanHideInactive() {
        when(mapper.findFarmCrops(FARM, false)).thenReturn(List.of(cropRow(1L, "딸기", "Y")));
        assertThat(service.listCrops(USER, FARM, false)).extracting(CatalogResponse::name).containsExactly("딸기");
        verify(guard).requireFarmMember(USER, FARM);
        verify(mapper).findFarmCrops(FARM, false);
    }

    @Test
    void mutationRequiresOwnerOrManager() {
        CatalogEntity crop = catalog(1L, null, "딸기");
        when(mapper.findCropByName("딸기")).thenReturn(Optional.of(crop));
        when(mapper.findFarmCrop(FARM, 1L)).thenReturn(Optional.empty(), Optional.of(cropRow(1L, "딸기", "Y")));
        service.createCrop(USER, FARM, new CatalogCreateRequest("딸기", 0));
        verify(guard).requireFarmRole(USER, FARM,
                FarmMemberEntity.ROLE_FARM_OWNER, FarmMemberEntity.ROLE_FARM_MANAGER);
    }

    @Test
    void cropRenameUsesCopyOnWriteAndPreservesOldCatalog() {
        FarmCatalogRow old = cropRow(1L, "딸기", "Y");
        FarmCatalogRow targetRow = cropRow(2L, "베리", "Y");
        FarmCatalogRow oldVariety = varietyRow(1L, 10L, "설향", "Y");
        when(mapper.findFarmCrop(FARM, 1L)).thenReturn(Optional.of(old));
        when(mapper.findCropByName("베리")).thenReturn(Optional.of(catalog(2L, null, "베리")));
        when(mapper.findFarmCrop(FARM, 2L)).thenReturn(Optional.empty(), Optional.of(targetRow));
        when(mapper.findFarmVarieties(FARM, 1L, true)).thenReturn(List.of(oldVariety));
        when(mapper.findVarietyByName(2L, "설향")).thenReturn(Optional.of(catalog(20L, 2L, "설향")));

        CatalogResponse result = service.updateCrop(USER, FARM, 1L, new CatalogUpdateRequest("베리", 3));

        assertThat(result.id()).isEqualTo(2L);
        verify(mapper).insertFarmCropIfAbsent(eq(FARM), eq(2L), eq(3), eq(USER), any());
        verify(mapper).updateFarmCrop(eq(FARM), eq(2L), eq(3), eq("Y"), eq(USER), any());
        verify(mapper).insertFarmVarietyIfAbsent(eq(FARM), eq(2L), eq(20L), eq(0), eq(USER), any());
        verify(mapper).migrateActiveSeasonsForCropVariety(eq(FARM), eq(1L), eq(10L), eq(2L), eq(20L), eq(USER), any());
        verify(mapper).migrateActiveSeasonsWithoutVariety(eq(FARM), eq(1L), eq(2L), eq(USER), any());
        verify(mapper).updateFarmCrop(eq(FARM), eq(1L), eq(0), eq("N"), eq(USER), any());
    }

    @Test
    void cropRenameCanReuseItsPreviouslyInactiveCatalogLink() {
        FarmCatalogRow old = cropRow(2L, "베리", "Y");
        FarmCatalogRow inactiveTarget = cropRow(1L, "딸기", "N");
        FarmCatalogRow reactivatedTarget = cropRow(1L, "딸기", "Y");
        when(mapper.findFarmCrop(FARM, 2L)).thenReturn(Optional.of(old));
        when(mapper.findCropByName("딸기")).thenReturn(Optional.of(catalog(1L, null, "딸기")));
        when(mapper.findFarmCrop(FARM, 1L)).thenReturn(Optional.of(inactiveTarget), Optional.of(reactivatedTarget));
        when(mapper.findFarmVarieties(FARM, 2L, true)).thenReturn(List.of());

        assertThat(service.updateCrop(USER, FARM, 2L, new CatalogUpdateRequest("딸기", 1)).activeYn()).isTrue();
        verify(mapper).updateFarmCrop(eq(FARM), eq(1L), eq(1), eq("Y"), eq(USER), any());
    }

    @Test
    void crossFarmResourceIsReportedAsNotFound() {
        when(mapper.findCustomer(FARM, 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deactivateCustomer(USER, FARM, 99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Test
    void varietyRenameMigratesOnlyActiveSeasonReferencesThroughScopedMapper() {
        FarmCatalogRow old = varietyRow(1L, 10L, "설향", "Y");
        FarmCatalogRow target = varietyRow(1L, 20L, "새설향", "Y");
        when(mapper.findFarmVariety(FARM, 1L, 10L)).thenReturn(Optional.of(old));
        when(mapper.findVarietyByName(1L, "새설향")).thenReturn(Optional.of(catalog(20L, 1L, "새설향")));
        when(mapper.findFarmVariety(FARM, 1L, 20L)).thenReturn(Optional.empty(), Optional.of(target));

        CatalogResponse response = service.updateVariety(USER, FARM, 1L, 10L,
                new CatalogUpdateRequest("새설향", 2));

        assertThat(response.id()).isEqualTo(20L);
        verify(mapper).migrateActiveSeasonsForVariety(eq(FARM), eq(1L), eq(10L), eq(20L), eq(USER), any());
        verify(mapper).updateFarmVariety(eq(FARM), eq(10L), eq(1L), eq(0), eq("N"), eq(USER), any());
    }

    @Test
    void cropSeasonRejectsEndBeforeStart() {
        when(mapper.findFarmCrop(FARM, 1L)).thenReturn(Optional.of(cropRow(1L, "딸기", "Y")));
        CropSeasonCreateRequest request = new CropSeasonCreateRequest(1L, null, "2026 작기",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 31));
        assertThatThrownBy(() -> service.createCropSeason(USER, FARM, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        verify(mapper, never()).insertCropSeason(any(), anyLong(), any());
    }

    @Test
    void cropSeasonNameOnlyPatchKeepsExistingInactiveCatalogSelection() {
        CropSeasonEntity season = season(CropSeasonEntity.STATUS_ACTIVE);
        season.setVarietyId(10L);
        when(mapper.findCropSeason(FARM, 4L)).thenReturn(Optional.of(season));
        CropSeasonUpdateRequest request = new CropSeasonUpdateRequest();
        request.setName("이름만 수정");

        CropSeasonResponse response = service.updateCropSeason(USER, FARM, 4L, request);

        assertThat(response.cropId()).isEqualTo(1L);
        assertThat(response.varietyId()).isEqualTo(10L);
        verify(mapper, never()).findFarmCrop(anyLong(), anyLong());
        verify(mapper, never()).findFarmVariety(anyLong(), anyLong(), anyLong());
        verify(mapper).updateCropSeason(eq(season), eq(USER), any());
    }

    @Test
    void cropSeasonSameSelectionPatchDoesNotRevalidateInactiveLinks() {
        CropSeasonEntity season = season(CropSeasonEntity.STATUS_ACTIVE);
        season.setVarietyId(10L);
        when(mapper.findCropSeason(FARM, 4L)).thenReturn(Optional.of(season));
        CropSeasonUpdateRequest request = new CropSeasonUpdateRequest();
        request.setCropId(1L);
        request.setVarietyId(10L);

        service.updateCropSeason(USER, FARM, 4L, request);

        verify(mapper, never()).findFarmCrop(anyLong(), anyLong());
        verify(mapper, never()).findFarmVariety(anyLong(), anyLong(), anyLong());
    }

    @Test
    void cropSeasonActualSelectionChangeRequiresActiveFarmLinks() {
        CropSeasonEntity season = season(CropSeasonEntity.STATUS_ACTIVE);
        season.setVarietyId(10L);
        when(mapper.findCropSeason(FARM, 4L)).thenReturn(Optional.of(season));
        when(mapper.findFarmCrop(FARM, 1L)).thenReturn(Optional.of(cropRow(1L, "딸기", "Y")));
        when(mapper.findFarmVariety(FARM, 1L, 11L)).thenReturn(Optional.of(varietyRow(1L, 11L, "금실", "Y")));
        CropSeasonUpdateRequest request = new CropSeasonUpdateRequest();
        request.setVarietyId(11L);

        assertThat(service.updateCropSeason(USER, FARM, 4L, request).varietyId()).isEqualTo(11L);
        verify(mapper).findFarmCrop(FARM, 1L);
        verify(mapper).findFarmVariety(FARM, 1L, 11L);
    }

    @Test
    void completedSeasonCannotBeMutatedAndCompletionIsIdempotent() {
        CropSeasonEntity season = season(CropSeasonEntity.STATUS_COMPLETED);
        when(mapper.findCropSeason(FARM, 4L)).thenReturn(Optional.of(season));
        assertThat(service.completeCropSeason(USER, FARM, 4L).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> service.updateCropSeason(USER, FARM, 4L, new CropSeasonUpdateRequest()))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).completeCropSeason(anyLong(), anyLong(), any(), anyLong(), any());
        verify(mutationGuard, never()).incrementStructureVersion(anyLong(), anyLong());
    }

    @Test
    void cropSeasonDeleteKeepsRowAndUsesSoftDelete() {
        when(mapper.findCropSeason(FARM, 4L)).thenReturn(Optional.of(season(CropSeasonEntity.STATUS_ACTIVE)));
        service.deleteCropSeason(USER, FARM, 4L);
        verify(mapper).softDeleteCropSeason(eq(FARM), eq(4L), eq(USER), any());
    }

    @Test
    void nullableCustomerPatchCanClearPhoneAndMemo() {
        LocalMasterDataRow row = customer(8L, "공판장", "010", "메모", "Y");
        when(mapper.findCustomer(FARM, 8L)).thenReturn(Optional.of(row));
        when(mapper.findCustomerByName(FARM, "공판장")).thenReturn(Optional.of(row));
        CustomerUpdateRequest request = new CustomerUpdateRequest();
        request.setPhone(null);
        request.setMemo(null);
        service.updateCustomer(USER, FARM, 8L, request);
        verify(mapper).updateCustomer(eq(FARM), eq(8L), eq("공판장"), eq("OTHER"), isNull(), isNull(),
                eq("Y"), eq(USER), any());
    }

    @Test
    void templateReuseReturnsStableCountsAndReactivatesLocalRows() {
        CatalogEntity crop = catalog(1L, null, "딸기");
        when(mapper.findCropByName("딸기")).thenReturn(Optional.of(crop));
        when(mapper.findFarmCrop(FARM, 1L)).thenReturn(Optional.of(cropRow(1L, "딸기", "N")));
        long varietyId = 10L;
        for (String name : List.of("설향", "금실", "킹스베리")) {
            CatalogEntity variety = catalog(varietyId, 1L, name);
            when(mapper.findVarietyByName(1L, name)).thenReturn(Optional.of(variety));
            when(mapper.findFarmVariety(FARM, 1L, varietyId)).thenReturn(Optional.of(varietyRow(1L, varietyId, name, "N")));
            varietyId++;
        }
        long workId = 20L;
        for (String name : List.of("정식", "유인", "적화·적과", "관수", "포장")) {
            when(mapper.findWorkTypeByName(FARM, name)).thenReturn(Optional.of(workType(workId++, name, "N")));
        }
        TemplateApplyResponse result = service.applyStrawberryTemplate(USER, FARM);
        assertThat(result.created()).isEqualTo(new TemplateApplyResponse.Counts(0, 0, 0));
        assertThat(result.reused()).isEqualTo(new TemplateApplyResponse.Counts(1, 3, 5));
        verify(mapper, times(5)).updateWorkType(eq(FARM), anyLong(), anyString(), anyInt(), eq("Y"), eq(USER), any());
        verify(mutationGuard).incrementStructureVersion(FARM, USER);
    }

    @Test
    void unchangedTemplateDoesNotIncreaseStructureVersion() {
        CatalogEntity crop = catalog(1L, null, "딸기");
        when(mapper.findCropByName("딸기")).thenReturn(Optional.of(crop));
        when(mapper.findFarmCrop(FARM, 1L)).thenReturn(Optional.of(cropRow(1L, "딸기", "Y")));
        long varietyId = 10L;
        int varietyOrder = 0;
        for (String name : List.of("설향", "금실", "킹스베리")) {
            CatalogEntity variety = catalog(varietyId, 1L, name);
            when(mapper.findVarietyByName(1L, name)).thenReturn(Optional.of(variety));
            FarmCatalogRow varietyLink = varietyRow(1L, varietyId, name, "Y");
            varietyLink.setDisplayOrder(varietyOrder++);
            when(mapper.findFarmVariety(FARM, 1L, varietyId))
                    .thenReturn(Optional.of(varietyLink));
            varietyId++;
        }
        long workId = 20L;
        int order = 0;
        for (String name : List.of("정식", "유인", "적화·적과", "관수", "포장")) {
            LocalMasterDataRow row = workType(workId++, name, "Y");
            row.setDisplayOrder(order++);
            when(mapper.findWorkTypeByName(FARM, name)).thenReturn(Optional.of(row));
        }

        service.applyStrawberryTemplate(USER, FARM);

        verify(mutationGuard, never()).incrementStructureVersion(anyLong(), anyLong());
    }

    private FarmCatalogRow cropRow(Long id, String name, String active) {
        FarmCatalogRow row = new FarmCatalogRow(); row.setFarmId(FARM); row.setCropId(id); row.setName(name);
        row.setDisplayOrder(0); row.setActiveYn(active); return row;
    }
    private FarmCatalogRow varietyRow(Long cropId, Long id, String name, String active) {
        FarmCatalogRow row = cropRow(cropId, name, active); row.setVarietyId(id); return row;
    }
    private CatalogEntity catalog(Long id, Long cropId, String name) {
        CatalogEntity row = new CatalogEntity(); row.setId(id); row.setCropId(cropId); row.setName(name); return row;
    }
    private LocalMasterDataRow workType(Long id, String name, String active) {
        LocalMasterDataRow row = new LocalMasterDataRow(); row.setId(id); row.setFarmId(FARM); row.setName(name);
        row.setDisplayOrder(0); row.setActiveYn(active); return row;
    }
    private LocalMasterDataRow customer(Long id, String name, String phone, String memo, String active) {
        LocalMasterDataRow row = workType(id, name, active); row.setType("OTHER"); row.setPhone(phone); row.setMemo(memo); return row;
    }
    private CropSeasonEntity season(String status) {
        CropSeasonEntity row = new CropSeasonEntity(); row.setId(4L); row.setFarmId(FARM); row.setCropId(1L);
        row.setName("작기"); row.setStartDate(LocalDate.of(2025, 9, 1)); row.setEndDate(LocalDate.of(2026, 5, 1));
        row.setStatus(status); return row;
    }
}
