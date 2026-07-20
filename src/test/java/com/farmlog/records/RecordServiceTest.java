package com.farmlog.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMembership;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmaccess.CareAccessGuard;
import com.farmlog.farmaccess.entity.CareAssignmentRow;
import com.farmlog.masterdata.entity.CropSeasonEntity;
import com.farmlog.masterdata.mapper.MasterDataMapper;
import com.farmlog.records.dto.BulkRequest;
import com.farmlog.records.dto.RecordMutationRequest;
import com.farmlog.records.dto.RecordResponse;
import com.farmlog.records.entity.RecordRow;
import com.farmlog.records.entity.ReferenceRow;
import com.farmlog.records.mapper.RecordMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class RecordServiceTest {
  private static final long USER = 7L;
  private static final long FARM = 11L;
  private RecordMapper mapper;
  private FarmMapper farmMapper;
  private MasterDataMapper masterDataMapper;
  private FarmAccessGuard guard;
  private CareAccessGuard careGuard;
  private RecordService service;

  @BeforeEach
  void setUp() {
    mapper = mock(RecordMapper.class);
    farmMapper = mock(FarmMapper.class);
    masterDataMapper = mock(MasterDataMapper.class);
    guard = mock(FarmAccessGuard.class);
    careGuard = mock(CareAccessGuard.class);
    service = new RecordService(mapper, farmMapper, masterDataMapper, guard, careGuard,
        mock(FarmMutationGuard.class));
    when(guard.requireFarmMember(USER, FARM))
        .thenReturn(new FarmMembership(FARM, USER, "FARM_OWNER"));
    when(guard.requireFarmRoleForUpdate(eq(USER), eq(FARM), any(String[].class)))
        .thenReturn(new FarmMembership(FARM, USER, "FARM_OWNER"));
    when(farmMapper.findById(FARM))
        .thenReturn(Optional.of(FarmEntity.builder().id(FARM).organizationId(3L).build()));
  }

  @Test
  void explicitNetAmountIsManualEvenWhenItEqualsCalculatedAmount() {
    RecordMutationRequest request = salesCreate();
    request.setNetAmountOverride(new BigDecimal("18"));
    ArgumentCaptor<RecordRow> rowCaptor = ArgumentCaptor.forClass(RecordRow.class);
    // insert 시 생성 키가 채워진 동일 행을 후속 조회가 반환하는 DB 동작을 모사한다.
    final RecordRow[] saved = new RecordRow[1];
    stubCustomer();
    when(mapper.insertSales(any()))
        .thenAnswer(
            invocation -> {
              saved[0] = invocation.getArgument(0);
              saved[0].setId(101L);
              return 1;
            });
    when(mapper.findById(RecordType.SALES, FARM, 101L))
        .thenAnswer(invocation -> Optional.of(saved[0]));

    RecordResponse response = service.create(USER, FARM, RecordType.SALES, request);

    verify(mapper).insertSales(rowCaptor.capture());
    assertThat(rowCaptor.getValue().getGrossAmount()).isEqualByComparingTo("20.00");
    assertThat(rowCaptor.getValue().getFeeAmount()).isEqualByComparingTo("2.00");
    assertThat(rowCaptor.getValue().getNetAmount()).isEqualByComparingTo("18.00");
    assertThat(rowCaptor.getValue().getNetAmountOverriddenYn()).isEqualTo("Y");
    assertThat(response.netAmountOverridden()).isTrue();
    assertThat(response.calculatedNetAmount()).isEqualByComparingTo("18.00");
  }

  @Test
  void componentPatchPreservesExistingManualNetAmountWhenOverrideIsOmitted() {
    RecordRow row = salesRow();
    row.setNetAmount(new BigDecimal("15.00"));
    row.setNetAmountOverriddenYn("Y");
    when(mapper.findById(RecordType.SALES, FARM, 101L)).thenReturn(Optional.of(row));
    when(mapper.updateSales(any(), eq(3L))).thenReturn(1);
    when(mapper.findCustomer(FARM, 9L, false)).thenReturn(Optional.of(ref(9L, "공판장", "Y")));
    RecordMutationRequest patch = new RecordMutationRequest();
    patch.setVersion(3L);
    patch.setQuantity(new BigDecimal("3"));

    service.update(USER, FARM, RecordType.SALES, 101L, patch);

    ArgumentCaptor<RecordRow> captor = ArgumentCaptor.forClass(RecordRow.class);
    verify(mapper).updateSales(captor.capture(), eq(3L));
    assertThat(captor.getValue().getGrossAmount()).isEqualByComparingTo("30.00");
    assertThat(captor.getValue().getNetAmount()).isEqualByComparingTo("15.00");
    assertThat(captor.getValue().getNetAmountOverriddenYn()).isEqualTo("Y");
  }

  @Test
  void rejectsCrossDomainFieldsAndImmutableClientRequestIdOnPatch() {
    RecordMutationRequest salesWithZone = salesCreate();
    salesWithZone.setZoneId(4L);
    assertValidation(() -> service.create(USER, FARM, RecordType.SALES, salesWithZone));
    verify(mapper, never()).insertSales(any());

    RecordMutationRequest workWithSalesField = new RecordMutationRequest();
    workWithSalesField.setVersion(1L);
    workWithSalesField.setFeeAmount(BigDecimal.ONE);
    assertValidation(() -> service.update(USER, FARM, RecordType.WORK, 1L, workWithSalesField));

    RecordMutationRequest immutableId = new RecordMutationRequest();
    immutableId.setVersion(1L);
    immutableId.setClientRequestId("123e4567-e89b-12d3-a456-426614174000");
    assertValidation(() -> service.update(USER, FARM, RecordType.WORK, 1L, immutableId));
    verify(mapper, never()).findById(eq(RecordType.WORK), eq(FARM), anyLong());
  }

  @Test
  void bulkAllowlistRejectsPackageAndSalesPresentationFields() {
    BulkRequest.Item item = new BulkRequest.Item(1L, 0L);
    RecordMutationRequest harvestChanges = new RecordMutationRequest();
    harvestChanges.setPackageUnit("상자");
    assertValidation(
        () ->
            service.bulk(
                USER,
                FARM,
                RecordType.HARVEST,
                new BulkRequest(BulkRequest.Operation.UPDATE, List.of(item), harvestChanges)));

    RecordMutationRequest salesChanges = new RecordMutationRequest();
    salesChanges.setItemName("딸기");
    assertValidation(
        () ->
            service.bulk(
                USER,
                FARM,
                RecordType.SALES,
                new BulkRequest(BulkRequest.Operation.UPDATE, List.of(item), salesChanges)));

    assertValidation(
        () ->
            service.bulk(
                USER,
                FARM,
                RecordType.WORK,
                new BulkRequest(
                    BulkRequest.Operation.DELETE, List.of(item), new RecordMutationRequest())));
    verify(mapper, never()).findById(any(), anyLong(), anyLong());
  }

  @Test
  void bulkChecksEveryVersionBeforeStartingWrites() {
    RecordRow first = workRow(1L, 0L, USER);
    RecordRow second = workRow(2L, 2L, USER);
    when(mapper.findById(RecordType.WORK, FARM, 1L)).thenReturn(Optional.of(first));
    when(mapper.findById(RecordType.WORK, FARM, 2L)).thenReturn(Optional.of(second));
    BulkRequest request =
        new BulkRequest(
            BulkRequest.Operation.DELETE,
            List.of(new BulkRequest.Item(1L, 0L), new BulkRequest.Item(2L, 1L)),
            null);

    assertThatThrownBy(() -> service.bulk(USER, FARM, RecordType.WORK, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    verify(mapper, never()).softDelete(any(), anyLong(), anyLong(), anyLong(), anyLong(), any());
    verify(mapper, never())
        .insertAudit(
            anyLong(),
            anyLong(),
            anyLong(),
            anyString(),
            anyString(),
            anyLong(),
            anyString(),
            any());
  }

  @Test
  void workerCanMutateOnlyOwnRecord() {
    when(guard.requireFarmMember(USER, FARM)).thenReturn(new FarmMembership(FARM, USER, "WORKER"));
    when(guard.requireFarmRoleForUpdate(eq(USER), eq(FARM), any(String[].class)))
        .thenReturn(new FarmMembership(FARM, USER, "WORKER"));
    RecordRow own = workRow(1L, 0L, USER);
    RecordRow other = workRow(2L, 0L, 99L);
    when(mapper.findById(RecordType.WORK, FARM, 1L)).thenReturn(Optional.of(own));
    when(mapper.findById(RecordType.WORK, FARM, 2L)).thenReturn(Optional.of(other));

    assertThat(service.detail(USER, FARM, RecordType.WORK, 1L).canEdit()).isTrue();
    assertThat(service.detail(USER, FARM, RecordType.WORK, 2L).canEdit()).isFalse();
    RecordMutationRequest patch = new RecordMutationRequest();
    patch.setVersion(0L);
    patch.setMemo("수정");
    assertThatThrownBy(() -> service.update(USER, FARM, RecordType.WORK, 2L, patch))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
  }

  @Test
  void unchangedInactiveReferencesRemainValidOnPatch() {
    RecordRow row = workRow(1L, 0L, USER);
    when(mapper.findById(RecordType.WORK, FARM, 1L)).thenReturn(Optional.of(row));
    when(mapper.updateWork(any(), eq(0L))).thenReturn(1);
    when(mapper.findZone(FARM, 4L, false)).thenReturn(Optional.of(ref(4L, "폐쇄 구역", "N")));
    when(mapper.findWorkType(FARM, 8L, false)).thenReturn(Optional.of(ref(8L, "이전 작업", "N")));
    RecordMutationRequest patch = new RecordMutationRequest();
    patch.setVersion(0L);
    patch.setMemo("과거 기록 정정");

    RecordResponse response = service.update(USER, FARM, RecordType.WORK, 1L, patch);

    assertThat(response.zone().activeYn()).isFalse();
    verify(mapper, never()).findZone(FARM, 4L, true);
    verify(mapper, never()).findWorkType(FARM, 8L, true);
  }

  @Test
  void clearingSeasonDoesNotRevalidateUnchangedInactiveCropAndVariety() {
    RecordRow row = workRow(1L, 0L, USER);
    row.setCropId(20L);
    row.setVarietyId(21L);
    row.setSeasonId(22L);
    when(mapper.findById(RecordType.WORK, FARM, 1L)).thenReturn(Optional.of(row));
    when(mapper.updateWork(any(), eq(0L))).thenReturn(1);
    when(mapper.findZone(FARM, 4L, false)).thenReturn(Optional.of(ref(4L, "폐쇄 구역", "N")));
    when(mapper.findCrop(FARM, 20L, false)).thenReturn(Optional.of(ref(20L, "이전 작물", "N")));
    when(mapper.findVariety(FARM, 20L, 21L, false)).thenReturn(Optional.of(ref(21L, "이전 품종", "N")));
    when(mapper.findWorkType(FARM, 8L, false)).thenReturn(Optional.of(ref(8L, "이전 작업", "N")));
    RecordMutationRequest patch = new RecordMutationRequest();
    patch.setVersion(0L);
    patch.setSeasonId(null);

    RecordResponse response = service.update(USER, FARM, RecordType.WORK, 1L, patch);

    assertThat(response.season()).isNull();
    assertThat(response.crop().activeYn()).isFalse();
    verify(mapper, never()).findCrop(FARM, 20L, true);
    verify(mapper, never()).findVariety(FARM, 20L, 21L, true);
  }

  @Test
  void explicitNullCropOrVarietyCannotBeSilentlyRestoredFromRemainingSeason() {
    RecordRow clearCropRow = workRow(1L, 0L, USER);
    clearCropRow.setCropId(20L);
    clearCropRow.setVarietyId(21L);
    clearCropRow.setSeasonId(22L);
    RecordRow clearVarietyRow = workRow(1L, 0L, USER);
    clearVarietyRow.setCropId(20L);
    clearVarietyRow.setVarietyId(21L);
    clearVarietyRow.setSeasonId(22L);
    when(mapper.findById(RecordType.WORK, FARM, 1L))
        .thenReturn(Optional.of(clearCropRow), Optional.of(clearVarietyRow));
    CropSeasonEntity season = new CropSeasonEntity();
    season.setFarmId(FARM);
    season.setCropId(20L);
    season.setVarietyId(21L);
    season.setStatus(CropSeasonEntity.STATUS_ACTIVE);
    when(masterDataMapper.findCropSeason(FARM, 22L)).thenReturn(Optional.of(season));

    RecordMutationRequest clearCrop = new RecordMutationRequest();
    clearCrop.setVersion(0L);
    clearCrop.setCropId(null);
    assertValidation(() -> service.update(USER, FARM, RecordType.WORK, 1L, clearCrop));

    RecordMutationRequest clearVariety = new RecordMutationRequest();
    clearVariety.setVersion(0L);
    clearVariety.setVarietyId(null);
    assertValidation(() -> service.update(USER, FARM, RecordType.WORK, 1L, clearVariety));
    verify(mapper, never()).updateWork(any(), anyLong());
  }

  @Test
  void careManagerCannotCreatePhaseFourRecords() {
    when(guard.requireFarmMember(USER, FARM))
        .thenReturn(new FarmMembership(FARM, USER, "FARM_CARE_MANAGER"));
    assertThatThrownBy(() -> service.create(USER, FARM, RecordType.SALES, salesCreate()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    verify(farmMapper, never()).findById(anyLong());
  }

  @Test
  void careManagerCreateLocksCurrentAssignmentAndForcesProvenance() {
    when(guard.requireFarmMember(USER, FARM)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
    CareAssignmentRow assignment = new CareAssignmentRow();
    assignment.setId(55L);
    when(careGuard.requireActiveForUpdate(USER, FARM)).thenReturn(assignment);
    stubCustomer();
    final RecordRow[] saved = new RecordRow[1];
    when(mapper.insertSales(any()))
        .thenAnswer(
            invocation -> {
              saved[0] = invocation.getArgument(0);
              saved[0].setId(101L);
              return 1;
            });
    when(mapper.findById(RecordType.SALES, FARM, 101L))
        .thenAnswer(invocation -> Optional.of(saved[0]));

    service.create(USER, FARM, RecordType.SALES, salesCreate());

    assertThat(saved[0].getCreatedRole()).isEqualTo("FARM_CARE_MANAGER");
    assertThat(saved[0].getCareAssignmentId()).isEqualTo(55L);
    assertThat(saved[0].getFarmerConfirmStatus()).isEqualTo("PENDING");
    verify(careGuard).requireActiveForUpdate(USER, FARM);
  }

  @Test
  void repeatedClientRequestReturnsExistingRecordWithoutAnotherInsert() {
    RecordRow existing = salesRow();
    existing.setClientRequestId("123e4567-e89b-12d3-a456-426614174000");
    when(mapper.findByClientRequestId(RecordType.SALES, FARM, existing.getClientRequestId()))
        .thenReturn(Optional.of(existing));
    when(mapper.findCustomer(FARM, 9L, false)).thenReturn(Optional.of(ref(9L, "공판장", "Y")));
    RecordMutationRequest request = salesCreate();
    request.setClientRequestId(existing.getClientRequestId());

    assertThat(service.create(USER, FARM, RecordType.SALES, request).id()).isEqualTo(101L);
    verify(mapper, never()).insertSales(any());
  }

  @Test
  void concurrentDuplicateClientRequestReturnsWinningInsert() {
    String requestId = "123e4567-e89b-12d3-a456-426614174000";
    RecordRow existing = salesRow();
    existing.setClientRequestId(requestId);
    when(mapper.findByClientRequestId(RecordType.SALES, FARM, requestId))
        .thenReturn(Optional.empty());
    when(mapper.findByClientRequestIdForUpdate(RecordType.SALES, FARM, requestId))
        .thenReturn(Optional.of(existing));
    when(mapper.insertSales(any())).thenThrow(new DuplicateKeyException("duplicate request"));
    stubCustomer();
    RecordMutationRequest request = salesCreate();
    request.setClientRequestId(requestId);

    assertThat(service.create(USER, FARM, RecordType.SALES, request).id()).isEqualTo(101L);
    verify(mapper).insertSales(any());
    verify(mapper).findByClientRequestId(RecordType.SALES, FARM, requestId);
    verify(mapper).findByClientRequestIdForUpdate(RecordType.SALES, FARM, requestId);
  }

  @Test
  void recordAuthorsIncludesHistoricalUsersAfterMembershipCheck() {
    when(mapper.findRecordAuthors(FARM))
        .thenReturn(List.of(ref(2L, "가나다", "Y"), ref(3L, "라마바", "Y")));
    assertThat(service.authors(USER, FARM))
        .extracting(RecordResponse.Actor::displayName)
        .containsExactly("가나다", "라마바");
    verify(guard).requireFarmMember(USER, FARM);
  }

  private RecordMutationRequest salesCreate() {
    RecordMutationRequest request = new RecordMutationRequest();
    request.setSalesDate(LocalDate.of(2026, 7, 1));
    request.setCustomerId(9L);
    request.setQuantity(new BigDecimal("2"));
    request.setUnit("kg");
    request.setUnitPrice(new BigDecimal("10"));
    request.setFeeAmount(new BigDecimal("2"));
    return request;
  }

  private RecordRow salesRow() {
    RecordRow row = base(RecordType.SALES, 101L, 3L, USER);
    row.setCustomerId(9L);
    row.setRecordDate(LocalDate.of(2026, 7, 1));
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

  private RecordRow workRow(long id, long version, long createdBy) {
    RecordRow row = base(RecordType.WORK, id, version, createdBy);
    row.setZoneId(4L);
    row.setWorkTypeId(8L);
    row.setRecordDate(LocalDate.of(2026, 7, 1));
    return row;
  }

  private RecordRow base(RecordType type, long id, long version, long createdBy) {
    RecordRow row = new RecordRow();
    row.setType(type);
    row.setId(id);
    row.setOrganizationId(3L);
    row.setFarmId(FARM);
    row.setVersion(version);
    row.setCreatedBy(createdBy);
    row.setCreatedByName("작성자");
    row.setCreatedRole(createdBy == USER ? "FARM_OWNER" : "WORKER");
    row.setFarmerConfirmStatus("NOT_REQUIRED");
    row.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
    return row;
  }

  private void stubCustomer() {
    when(mapper.findCustomer(FARM, 9L, true)).thenReturn(Optional.of(ref(9L, "공판장", "Y")));
    when(mapper.findCustomer(FARM, 9L, false)).thenReturn(Optional.of(ref(9L, "공판장", "Y")));
  }

  private ReferenceRow ref(long id, String name, String active) {
    ReferenceRow row = new ReferenceRow();
    row.setId(id);
    row.setName(name);
    row.setActiveYn(active);
    return row;
  }

  private void assertValidation(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }
}
