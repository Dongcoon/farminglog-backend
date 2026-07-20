package com.farmlog.records;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMembership;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.masterdata.entity.CropSeasonEntity;
import com.farmlog.masterdata.mapper.MasterDataMapper;
import com.farmlog.records.dto.PageResponse;
import com.farmlog.records.dto.RecordMutationRequest;
import com.farmlog.records.entity.FeedRow;
import com.farmlog.records.entity.RecordRow;
import com.farmlog.records.entity.ReferenceRow;
import com.farmlog.records.mapper.RecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecordServiceValidationTest {
    private static final long USER = 7L;
    private static final long FARM = 11L;
    private RecordMapper mapper;
    private FarmMapper farmMapper;
    private MasterDataMapper masterDataMapper;
    private FarmAccessGuard guard;
    private RecordService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RecordMapper.class);
        farmMapper = mock(FarmMapper.class);
        masterDataMapper = mock(MasterDataMapper.class);
        guard = mock(FarmAccessGuard.class);
        service = new RecordService(mapper, farmMapper, masterDataMapper, guard, null,
                mock(FarmMutationGuard.class));
        when(guard.requireFarmMember(USER, FARM)).thenReturn(new FarmMembership(FARM, USER, "FARM_OWNER"));
        when(guard.requireFarmRoleForUpdate(eq(USER), eq(FARM), any(String[].class)))
                .thenReturn(new FarmMembership(FARM, USER, "FARM_OWNER"));
        when(farmMapper.findById(FARM)).thenReturn(Optional.of(FarmEntity.builder().id(FARM).organizationId(3L).build()));
    }

    @Test
    void allFourRecordTypesHaveCreateHappyPaths() {
        RecordMutationRequest work = baseDated(RecordType.WORK);
        work.setZoneId(4L);
        work.setWorkTypeId(8L);
        assertThat(create(RecordType.WORK, work).getType()).isEqualTo(RecordType.WORK);

        RecordMutationRequest pest = baseDated(RecordType.PEST_CONTROL);
        pest.setZoneId(4L);
        pest.setChemicalName("친환경 약제");
        pest.setAmountValue(new BigDecimal("1.5"));
        pest.setAmountUnit("L");
        assertThat(create(RecordType.PEST_CONTROL, pest).getType()).isEqualTo(RecordType.PEST_CONTROL);

        RecordMutationRequest harvest = baseDated(RecordType.HARVEST);
        harvest.setZoneId(4L);
        harvest.setQuantity(new BigDecimal("12.50"));
        harvest.setUnit("kg");
        assertThat(create(RecordType.HARVEST, harvest).getType()).isEqualTo(RecordType.HARVEST);

        RecordMutationRequest sales = baseDated(RecordType.SALES);
        sales.setCustomerId(9L);
        sales.setQuantity(new BigDecimal("2"));
        sales.setUnit("kg");
        sales.setUnitPrice(new BigDecimal("1000"));
        assertThat(create(RecordType.SALES, sales).getType()).isEqualTo(RecordType.SALES);
    }

    @Test
    void requiredAndFutureDatesAreRejected() {
        RecordMutationRequest missing = new RecordMutationRequest();
        missing.setZoneId(4L);
        missing.setWorkTypeId(8L);
        stubActiveReferences();
        assertValidation(() -> service.create(USER, FARM, RecordType.WORK, missing));

        RecordMutationRequest future = baseDated(RecordType.HARVEST);
        future.setHarvestDate(LocalDate.now().plusDays(1));
        future.setZoneId(4L);
        future.setQuantity(BigDecimal.ONE);
        future.setUnit("kg");
        assertValidation(() -> service.create(USER, FARM, RecordType.HARVEST, future));
    }

    @Test
    void pestAmountAndUnitMoveAsNullablePair() {
        RecordMutationRequest invalid = baseDated(RecordType.PEST_CONTROL);
        invalid.setZoneId(4L);
        invalid.setChemicalName("약제");
        invalid.setAmountValue(BigDecimal.ONE);
        stubActiveReferences();
        assertValidation(() -> service.create(USER, FARM, RecordType.PEST_CONTROL, invalid));

        RecordRow existing = row(RecordType.PEST_CONTROL, 5L, 2L);
        existing.setZoneId(4L);
        existing.setChemicalName("약제");
        existing.setAmountValue(BigDecimal.ONE);
        existing.setAmountUnit("L");
        when(mapper.findById(RecordType.PEST_CONTROL, FARM, 5L)).thenReturn(Optional.of(existing));
        when(mapper.updatePest(any(), eq(2L))).thenReturn(1);
        when(mapper.findZone(FARM, 4L, false)).thenReturn(Optional.of(ref(4L, "1구역")));
        RecordMutationRequest clear = new RecordMutationRequest();
        clear.setVersion(2L);
        clear.setAmountValue(null);
        clear.setAmountUnit(null);

        service.update(USER, FARM, RecordType.PEST_CONTROL, 5L, clear);
        assertThat(existing.getAmountValue()).isNull();
        assertThat(existing.getAmountUnit()).isNull();
    }

    @Test
    void salesRejectsFeeAndManualNetOutsideGrossRange() {
        RecordMutationRequest fee = salesRequest();
        fee.setFeeAmount(new BigDecimal("21"));
        stubActiveReferences();
        assertValidation(() -> service.create(USER, FARM, RecordType.SALES, fee));

        RecordMutationRequest net = salesRequest();
        net.setNetAmountOverride(new BigDecimal("21"));
        assertValidation(() -> service.create(USER, FARM, RecordType.SALES, net));
    }

    @Test
    void nullOverrideReturnsToAutoAndAutoModeRecalculatesComponents() {
        RecordRow manual = salesRow();
        manual.setNetAmount(new BigDecimal("15.00"));
        manual.setNetAmountOverriddenYn("Y");
        when(mapper.findById(RecordType.SALES, FARM, 5L)).thenReturn(Optional.of(manual));
        when(mapper.updateSales(any(), anyLong())).thenReturn(1);
        when(mapper.findCustomer(FARM, 9L, false)).thenReturn(Optional.of(ref(9L, "공판장")));
        RecordMutationRequest auto = new RecordMutationRequest();
        auto.setVersion(2L);
        auto.setNetAmountOverride(null);
        service.update(USER, FARM, RecordType.SALES, 5L, auto);
        assertThat(manual.getNetAmount()).isEqualByComparingTo("18.00");
        assertThat(manual.getNetAmountOverriddenYn()).isEqualTo("N");

        manual.setVersion(3L);
        RecordMutationRequest quantity = new RecordMutationRequest();
        quantity.setVersion(3L);
        quantity.setQuantity(new BigDecimal("3"));
        service.update(USER, FARM, RecordType.SALES, 5L, quantity);
        assertThat(manual.getGrossAmount()).isEqualByComparingTo("30.00");
        assertThat(manual.getNetAmount()).isEqualByComparingTo("28.00");
    }

    @Test
    void cropSeasonMismatchAndCrossFarmReferenceAreRejected() {
        RecordMutationRequest mismatch = baseDated(RecordType.WORK);
        mismatch.setZoneId(4L);
        mismatch.setWorkTypeId(8L);
        mismatch.setCropId(1L);
        mismatch.setSeasonId(6L);
        stubActiveReferences();
        CropSeasonEntity season = new CropSeasonEntity();
        season.setFarmId(FARM);
        season.setCropId(2L);
        season.setStatus(CropSeasonEntity.STATUS_ACTIVE);
        when(masterDataMapper.findCropSeason(FARM, 6L)).thenReturn(Optional.of(season));
        assertValidation(() -> service.create(USER, FARM, RecordType.WORK, mismatch));

        reset(mapper);
        RecordMutationRequest crossFarm = baseDated(RecordType.WORK);
        crossFarm.setZoneId(999L);
        crossFarm.setWorkTypeId(8L);
        assertThatThrownBy(() -> service.create(USER, FARM, RecordType.WORK, crossFarm))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FARM_ZONE_NOT_FOUND));
    }

    @Test
    void optimisticVersionMismatchRejectsUpdateAndDeleteWithoutWrites() {
        RecordRow existing = row(RecordType.WORK, 5L, 3L);
        existing.setZoneId(4L);
        existing.setWorkTypeId(8L);
        when(mapper.findById(RecordType.WORK, FARM, 5L)).thenReturn(Optional.of(existing));
        RecordMutationRequest patch = new RecordMutationRequest();
        patch.setVersion(2L);
        patch.setMemo("충돌");

        assertConflict(() -> service.update(USER, FARM, RecordType.WORK, 5L, patch));
        assertConflict(() -> service.delete(USER, FARM, RecordType.WORK, 5L, 2L));
        verify(mapper, never()).updateWork(any(), anyLong());
        verify(mapper, never()).softDelete(any(), anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void listRejectsInvalidPageDateRangeAndSort() {
        assertValidation(() -> list(-1, 20, null, null, null));
        assertValidation(() -> list(0, 101, null, null, null));
        assertValidation(() -> list(Integer.MAX_VALUE, 100, null, null, null));
        assertValidation(() -> list(0, 20, LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1), null));
        assertValidation(() -> list(0, 20, null, null, "drop table,asc"));
        verify(mapper, never()).findPage(any(), any());
    }

    @Test
    void salesListRejectsBlankAndUnknownSettlementStatus() {
        assertValidation(() -> salesList(" "));
        assertValidation(() -> salesList("SETTLED"));
        verify(mapper, never()).findPage(any(), any());
        verify(mapper, never()).countPage(any(), any());
    }

    @Test
    void feedDefaultsAllTypesAndUsesOneGlobalPageAndCount() {
        FeedRow row = new FeedRow();
        row.setType(RecordType.WORK); row.setId(1L); row.setZoneId(4L); row.setZoneName("폐쇄 구역");
        row.setZoneActiveYn("N"); row.setRecordDate(LocalDate.of(2026, 7, 1)); row.setCreatedBy(USER);
        row.setCreatedRole("FARM_OWNER"); row.setFarmerConfirmStatus("NOT_REQUIRED"); row.setCreatedAt(LocalDateTime.now());
        when(mapper.findFeed(eq(FARM), isNull(), isNull(), isNull(), isNull(), anyList(),
                eq("q.record_date"), eq("DESC"), eq(20), eq(10))).thenReturn(List.of(row));
        when(mapper.countFeed(eq(FARM), isNull(), isNull(), isNull(), isNull(), anyList())).thenReturn(25L);

        PageResponse<?> page = service.feed(USER, FARM, null, null, null, null, null, 2, 10, null);

        assertThat(page.totalElements()).isEqualTo(25L);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(((com.farmlog.records.dto.FeedResponse) page.content().get(0)).zone().activeYn()).isFalse();
        verify(mapper).findFeed(eq(FARM), isNull(), isNull(), isNull(), isNull(),
                eq(List.of(RecordType.values())), eq("q.record_date"), eq("DESC"), eq(20), eq(10));
        verify(mapper).countFeed(eq(FARM), isNull(), isNull(), isNull(), isNull(), eq(List.of(RecordType.values())));
    }

    private RecordRow create(RecordType type, RecordMutationRequest request) {
        reset(mapper);
        stubActiveReferences();
        final RecordRow[] saved = new RecordRow[1];
        org.mockito.stubbing.Answer<Integer> answer = invocation -> {
            saved[0] = invocation.getArgument(0);
            saved[0].setId(100L + type.ordinal());
            return 1;
        };
        switch (type) {
            case WORK -> when(mapper.insertWork(any())).thenAnswer(answer);
            case PEST_CONTROL -> when(mapper.insertPest(any())).thenAnswer(answer);
            case HARVEST -> when(mapper.insertHarvest(any())).thenAnswer(answer);
            case SALES -> when(mapper.insertSales(any())).thenAnswer(answer);
        }
        when(mapper.findById(eq(type), eq(FARM), anyLong())).thenAnswer(invocation -> Optional.of(saved[0]));
        service.create(USER, FARM, type, request);
        return saved[0];
    }

    private RecordMutationRequest baseDated(RecordType type) {
        RecordMutationRequest request = new RecordMutationRequest();
        LocalDate date = LocalDate.of(2026, 7, 1);
        switch (type) {
            case WORK -> request.setWorkDate(date);
            case PEST_CONTROL -> request.setApplyDate(date);
            case HARVEST -> request.setHarvestDate(date);
            case SALES -> request.setSalesDate(date);
        }
        return request;
    }

    private RecordMutationRequest salesRequest() {
        RecordMutationRequest request = baseDated(RecordType.SALES);
        request.setCustomerId(9L);
        request.setQuantity(new BigDecimal("2"));
        request.setUnit("kg");
        request.setUnitPrice(new BigDecimal("10"));
        request.setFeeAmount(new BigDecimal("2"));
        return request;
    }

    private RecordRow salesRow() {
        RecordRow row = row(RecordType.SALES, 5L, 2L);
        row.setCustomerId(9L);
        row.setQuantity(new BigDecimal("2.00"));
        row.setUnit("kg");
        row.setUnitPrice(new BigDecimal("10.00"));
        row.setGrossAmount(new BigDecimal("20.00"));
        row.setFeeAmount(new BigDecimal("2.00"));
        row.setNetAmount(new BigDecimal("18.00"));
        row.setNetAmountOverriddenYn("N");
        row.setSettlementStatus("PENDING");
        return row;
    }

    private RecordRow row(RecordType type, long id, long version) {
        RecordRow row = new RecordRow();
        row.setType(type);
        row.setId(id);
        row.setOrganizationId(3L);
        row.setFarmId(FARM);
        row.setRecordDate(LocalDate.of(2026, 7, 1));
        row.setCreatedBy(USER);
        row.setCreatedByName("작성자");
        row.setCreatedRole("FARM_OWNER");
        row.setFarmerConfirmStatus("NOT_REQUIRED");
        row.setCreatedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        row.setVersion(version);
        return row;
    }

    private void stubActiveReferences() {
        when(mapper.findZone(eq(FARM), anyLong(), eq(true))).thenAnswer(invocation -> Optional.of(ref(invocation.getArgument(1), "구역")));
        when(mapper.findCurrentZoneForUpdate(eq(FARM), anyLong())).thenAnswer(invocation -> Optional.of(ref(invocation.getArgument(1), "구역")));
        when(mapper.findZone(eq(FARM), anyLong(), eq(false))).thenAnswer(invocation -> Optional.of(ref(invocation.getArgument(1), "구역")));
        when(mapper.findWorkType(eq(FARM), anyLong(), anyBoolean())).thenAnswer(invocation -> Optional.of(ref(invocation.getArgument(1), "작업")));
        when(mapper.findCustomer(eq(FARM), anyLong(), anyBoolean())).thenAnswer(invocation -> Optional.of(ref(invocation.getArgument(1), "공판장")));
        when(mapper.findCrop(eq(FARM), anyLong(), anyBoolean())).thenAnswer(invocation -> Optional.of(ref(invocation.getArgument(1), "작물")));
    }

    private ReferenceRow ref(long id, String name) {
        ReferenceRow row = new ReferenceRow();
        row.setId(id);
        row.setName(name);
        row.setActiveYn("Y");
        return row;
    }

    private void list(int page, int size, LocalDate from, LocalDate to, String sort) {
        service.list(USER, FARM, RecordType.WORK, from, to, null, null, null, null,
                null, null, null, null, page, size, sort);
    }

    private void salesList(String settlementStatus) {
        service.list(USER, FARM, RecordType.SALES, null, null, null, null, null, null,
                null, null, null, settlementStatus, 0, 20, null);
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
