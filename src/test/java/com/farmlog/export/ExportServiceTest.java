package com.farmlog.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.common.tenant.FarmMembership;
import com.farmlog.common.tenant.FarmMutationGuard;
import com.farmlog.export.dto.ExportCreateRequest;
import com.farmlog.export.entity.ExportJobRow;
import com.farmlog.export.mapper.ExportMapper;
import com.farmlog.farm.entity.FarmEntity;
import com.farmlog.farm.mapper.FarmMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportServiceTest {
    private static final long USER = 7L, FARM = 11L;
    private ExportMapper mapper;
    private FarmMapper farmMapper;
    private FarmAccessGuard guard;
    private ExportStorage storage;
    private ExportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ExportMapper.class);
        farmMapper = mock(FarmMapper.class);
        guard = mock(FarmAccessGuard.class);
        storage = mock(ExportStorage.class);
        ExportProperties properties = new ExportProperties();
        FarmMutationGuard mutationGuard = mock(FarmMutationGuard.class);
        service = new ExportService(mapper, farmMapper, guard, properties, storage,
                new ObjectMapper().findAndRegisterModules(), mutationGuard);
        when(guard.requireFarmMember(USER, FARM)).thenReturn(new FarmMembership(FARM, USER, "FARM_OWNER"));
        when(farmMapper.findById(FARM)).thenReturn(Optional.of(FarmEntity.builder().id(FARM).organizationId(3L).build()));
        when(mutationGuard.lockActiveFarm(FARM))
                .thenReturn(FarmEntity.builder().id(FARM).organizationId(3L).build());
    }

    @Test
    void duplicateInsertUsesCurrentRead() {
        ExportCreateRequest request = request();
        ExportJobRow winner = job(ExportStatus.REQUESTED, LocalDateTime.now().plusDays(7));
        when(mapper.findByClientRequestId(FARM, request.clientRequestId())).thenReturn(Optional.empty());
        when(mapper.insertJob(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.findByClientRequestIdForUpdate(FARM, request.clientRequestId())).thenReturn(Optional.of(winner));

        assertThat(service.create(USER, FARM, request).id()).isEqualTo(31L);

        verify(mapper).findByClientRequestIdForUpdate(FARM, request.clientRequestId());
    }

    @Test
    void responseDerivesExpiredStatusAtRetentionBoundary() {
        ExportJobRow row = job(ExportStatus.COMPLETED, LocalDateTime.now().minusNanos(1));
        when(mapper.findById(FARM, 31L)).thenReturn(Optional.of(row));

        var response = service.detail(USER, FARM, 31L);

        assertThat(response.status()).isEqualTo(ExportStatus.EXPIRED);
        assertThat(response.canDownload()).isFalse();
    }

    @Test
    void downloadAuditUsesActualDownloader() {
        ExportJobRow row = job(ExportStatus.COMPLETED, LocalDateTime.now().plusDays(1));
        when(mapper.findById(FARM, 31L)).thenReturn(Optional.of(row));
        when(storage.resolveExisting("11/file.xlsx")).thenReturn(Path.of("file.xlsx"));

        service.download(USER, FARM, 31L);

        verify(mapper).insertAudit(eq(3L), eq(FARM), eq(USER), eq("EXPORT_DOWNLOAD"), eq(31L), anyString(), any());
    }

    @Test
    void missingPhysicalFileTransitionsJobAndAttachmentToExpired() {
        ExportJobRow row = job(ExportStatus.COMPLETED, LocalDateTime.now().plusDays(1));
        when(mapper.findById(FARM, 31L)).thenReturn(Optional.of(row));
        when(storage.resolveExisting("11/file.xlsx")).thenThrow(new BusinessException(ErrorCode.EXPORT_FILE_MISSING));
        when(mapper.markExpired(eq(31L), any())).thenReturn(1);

        assertThatThrownBy(() -> service.download(USER, FARM, 31L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXPORT_FILE_MISSING));

        verify(mapper).markExpired(eq(31L), any());
        verify(mapper).softDeleteFile(eq(41L), any());
        verify(storage).deleteQuietly("11/file.xlsx");
        verify(mapper).insertAudit(eq(3L), eq(FARM), isNull(), eq("EXPORT_EXPIRED"), eq(31L), contains("FILE_MISSING"), any());
    }

    private ExportCreateRequest request() {
        return new ExportCreateRequest("123e4567-e89b-12d3-a456-426614174000", ExportFormat.XLSX,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), List.of(ExportScope.WORK), ConfirmationFilter.ALL);
    }

    private ExportJobRow job(ExportStatus status, LocalDateTime expiresAt) {
        ExportJobRow row = new ExportJobRow();
        row.setId(31L); row.setOrganizationId(3L); row.setFarmId(FARM); row.setFileId(41L);
        row.setExportType("XLSX"); row.setScopesJson("[\"WORK\"]"); row.setConfirmationFilter("ALL");
        row.setClientRequestId("123e4567-e89b-12d3-a456-426614174000");
        row.setPeriodStart(LocalDate.of(2026, 7, 1)); row.setPeriodEnd(LocalDate.of(2026, 7, 31));
        row.setStatus(status.name()); row.setRequestedBy(5L); row.setRequestedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        row.setCompletedAt(LocalDateTime.of(2026, 7, 1, 10, 0)); row.setExpiresAt(expiresAt);
        row.setOriginalFileName("file.xlsx"); row.setContentType("application/octet-stream"); row.setFileSize(10L);
        row.setStoragePath("11/file.xlsx");
        return row;
    }
}
