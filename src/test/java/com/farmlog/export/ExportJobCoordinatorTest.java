package com.farmlog.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlog.export.entity.ExportJobRow;
import com.farmlog.export.entity.GeneratedFile;
import com.farmlog.export.mapper.ExportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportJobCoordinatorTest {
    private ExportMapper mapper;
    private ExportProperties properties;
    private ExportJobCoordinator coordinator;

    @BeforeEach
    void setUp() {
        mapper = mock(ExportMapper.class);
        properties = new ExportProperties();
        coordinator = new ExportJobCoordinator(mapper, properties, new ObjectMapper());
    }

    @Test
    void exhaustedStaleJobIsFailedBeforeClaimingAnotherJob() {
        ExportJobRow exhausted = job();
        exhausted.setAttemptCount(3);
        when(mapper.findExhaustedStale(any(), eq(3))).thenReturn(List.of(exhausted));
        when(mapper.markExhaustedFailed(eq(31L), eq("RETRY_EXHAUSTED"), anyString(), any(), eq(3))).thenReturn(1);
        when(mapper.findClaimCandidate(any(), eq(3))).thenReturn(Optional.empty());

        assertThat(coordinator.claim()).isEmpty();

        verify(mapper).insertAudit(eq(3L), eq(11L), isNull(), eq("EXPORT_FAILED"), eq(31L), contains("SYSTEM"), any());
    }

    @Test
    void heartbeatRejectsStaleClaim() {
        when(mapper.renewLease(eq(31L), eq("claim-a"), any())).thenReturn(0);
        assertThatThrownBy(() -> coordinator.renew(31L, "claim-a"))
                .isInstanceOf(StaleExportClaimException.class);
    }

    @Test
    void completionIsGuardedByClaimToken() {
        ExportJobRow job = job();
        GeneratedFile generated = new GeneratedFile("a.xlsx", "stored.xlsx", "type", 10L, Path.of("11/stored.xlsx"));
        doAnswer(invocation -> {
            invocation.<com.farmlog.export.entity.ExportFileRow>getArgument(0).setId(41L);
            return null;
        }).when(mapper).insertFile(any());
        when(mapper.markCompleted(eq(31L), anyLong(), any(), any(), eq("claim-a"))).thenReturn(0);

        assertThatThrownBy(() -> coordinator.complete(job, generated))
                .isInstanceOf(StaleExportClaimException.class);

        verify(mapper).markCompleted(eq(31L), anyLong(), any(), any(), eq("claim-a"));
    }

    @Test
    void claimAssignsFreshTokenAndReturnsWorkerSnapshot() {
        ExportJobRow candidate = job();
        candidate.setClaimToken(null);
        ExportJobRow claimed = job();
        when(mapper.findExhaustedStale(any(), eq(3))).thenReturn(List.of());
        when(mapper.findClaimCandidate(any(), eq(3))).thenReturn(Optional.of(candidate));
        when(mapper.markProcessing(eq(31L), anyString(), any(), any())).thenReturn(1);
        when(mapper.findWorkerJob(31L)).thenReturn(Optional.of(claimed));

        assertThat(coordinator.claim()).contains(claimed);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mapper).markProcessing(eq(31L), token.capture(), any(), any());
        assertThat(token.getValue()).isNotBlank();
    }

    private ExportJobRow job() {
        ExportJobRow row = new ExportJobRow();
        row.setId(31L); row.setOrganizationId(3L); row.setFarmId(11L); row.setRequestedBy(7L);
        row.setExportType("XLSX"); row.setClaimToken("claim-a"); row.setAttemptCount(1);
        return row;
    }
}
