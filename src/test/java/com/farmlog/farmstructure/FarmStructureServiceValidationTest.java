package com.farmlog.farmstructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farm.mapper.FarmZoneMapper;
import com.farmlog.farmstructure.dto.StructureDtos.ConfirmRequest;
import com.farmlog.farmstructure.dto.StructureDtos.MergePreviewRequest;
import com.farmlog.farmstructure.dto.StructureDtos.Resolution;
import com.farmlog.farmstructure.dto.StructureDtos.SplitPreviewRequest;
import com.farmlog.farmstructure.dto.StructureDtos.Target;
import com.farmlog.farmstructure.entity.StructureEventRow;
import com.farmlog.farmstructure.entity.StructureFarmRow;
import com.farmlog.farmstructure.mapper.FarmStructureMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FarmStructureServiceValidationTest {
  private FarmStructureMapper mapper;
  private FarmStructureService service;

  @BeforeEach
  void setUp() {
    mapper = mock(FarmStructureMapper.class);
    service = new FarmStructureService(
        mapper, mock(FarmMapper.class), mock(FarmZoneMapper.class),
        mock(StructureAccessGuard.class), new StructureCanonicalJson());
  }

  @Test
  void splitCannotReuseItsSourceFarmAsTarget() {
    String requestId = "123e4567-e89b-12d3-a456-426614174000";
    SplitPreviewRequest request = new SplitPreviewRequest(
        requestId, 11L, new Target("EXISTING", 11L, null, null), List.of(5L),
        LocalDate.now(), "RECORDED_ONLY", null, List.of("CROP_VARIETY"), List.of(), null);
    when(mapper.findByPreviewRequest(7L, requestId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.previewSplit(7L, request))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  void expiredDraftCannotBeConfirmed() {
    String requestId = "123e4567-e89b-12d3-a456-426614174001";
    StructureEventRow draft = new StructureEventRow();
    draft.setId(9L);
    draft.setEventType("MERGE");
    draft.setStatus("DRAFT");
    draft.setPreviewExpiresAt(LocalDateTime.now().minusSeconds(1));
    when(mapper.findByConfirmRequest(7L, requestId)).thenReturn(Optional.empty());
    when(mapper.findEvent(9L)).thenReturn(Optional.of(draft));

    assertThatThrownBy(() -> service.confirm(
        7L, "MERGE", new ConfirmRequest(requestId, 9L, 0L, "hash")))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PREVIEW_EXPIRED));
  }

  @Test
  void nullTargetModeIsReportedAsValidationError() {
    assertValidation(merge(new Target(null, null, null, null), "RECORDED_ONLY",
        List.of("CROP_VARIETY"), List.of()));
  }

  @Test
  void nullPolicyAndNullScopeAreReportedAsValidationErrors() {
    Target target = new Target("NEW", null, "통합농장", 7L);
    assertValidation(merge(target, null, List.of("CROP_VARIETY"), List.of()));
    assertValidation(merge(target, "RECORDED_ONLY",
        Arrays.asList("CROP_VARIETY", null), List.of()));
  }

  @Test
  void nullResolutionAndConflictIdAreReportedBeforeSorting() {
    Target target = new Target("NEW", null, "통합농장", 7L);
    assertValidation(merge(target, "RECORDED_ONLY", List.of("CROP_VARIETY"),
        Arrays.asList((Resolution) null)));
    assertValidation(merge(target, "RECORDED_ONLY", List.of("CROP_VARIETY"),
        List.of(new Resolution(null, "CROP_VARIETY", "SKIP", null, null, null))));
  }

  @Test
  void mergeEffectiveDateCannotPrecedeSeparateExistingTargetCreation() {
    String requestId = "123e4567-e89b-12d3-a456-426614174080";
    LocalDate effectiveDate = LocalDate.now().minusDays(1);
    MergePreviewRequest request = new MergePreviewRequest(requestId, List.of(11L, 12L),
        new Target("EXISTING", 13L, null, null), effectiveDate, "RECORDED_ONLY", null,
        List.of("CROP_VARIETY"), List.of(), null);
    when(mapper.findByPreviewRequest(7L, requestId)).thenReturn(Optional.empty());
    when(mapper.findFarmsByIds(List.of(11L, 12L, 13L))).thenReturn(List.of(
        farm(11L, effectiveDate.minusDays(10)), farm(12L, effectiveDate.minusDays(5)),
        farm(13L, effectiveDate.plusDays(1))));

    assertValidation(request);
  }

  @Test
  void splitEffectiveDateCannotPrecedeExistingTargetCreation() {
    String requestId = "123e4567-e89b-12d3-a456-426614174081";
    LocalDate effectiveDate = LocalDate.now().minusDays(1);
    SplitPreviewRequest request = new SplitPreviewRequest(requestId, 11L,
        new Target("EXISTING", 13L, null, null), List.of(5L), effectiveDate,
        "RECORDED_ONLY", null, List.of("CROP_VARIETY"), List.of(), null);
    when(mapper.findByPreviewRequest(7L, requestId)).thenReturn(Optional.empty());
    when(mapper.findFarmsByIds(List.of(11L, 13L))).thenReturn(List.of(
        farm(11L, effectiveDate.minusDays(10)), farm(13L, effectiveDate.plusDays(1))));

    assertThatThrownBy(() -> service.previewSplit(7L, request))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  private MergePreviewRequest merge(Target target, String policy, List<String> scopes,
      List<Resolution> resolutions) {
    return new MergePreviewRequest("123e4567-e89b-12d3-a456-426614174099",
        List.of(11L, 12L), target, LocalDate.now(), policy, null, scopes, resolutions, null);
  }

  private void assertValidation(MergePreviewRequest request) {
    assertThatThrownBy(() -> service.previewMerge(7L, request))
        .isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  private StructureFarmRow farm(Long id, LocalDate createdDate) {
    StructureFarmRow farm = new StructureFarmRow();
    farm.setId(id);
    farm.setOrganizationId(99L);
    farm.setStatus("ACTIVE");
    farm.setLifecycleStatus("ACTIVE");
    farm.setCreatedAt(createdDate.atStartOfDay());
    return farm;
  }
}
