package com.farmlog.export;

import com.farmlog.export.entity.ExportJobRow;
import com.farmlog.export.mapper.ExportMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportFileGeneratorTest {
    @TempDir Path tempDir;

    @Test
    void summaryOnlyXlsxIsPublishedAndHeartbeatsClaim() throws Exception {
        ExportMapper mapper = mock(ExportMapper.class);
        SqlSessionFactory sessions = mock(SqlSessionFactory.class);
        ExportJobCoordinator coordinator = mock(ExportJobCoordinator.class);
        ExportProperties properties = new ExportProperties();
        properties.setRootDir(tempDir.toString());
        ExportStorage storage = new ExportStorage(properties);
        when(mapper.findSummaryCounts(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(mapper.findSummaryHarvest(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(mapper.findSummaryAveragePrices(anyLong(), any(), any(), any())).thenReturn(List.of());
        ExportJobRow job = job(ExportFormat.XLSX, "[\"REPORT_SUMMARY\"]");

        var generated = new ExportFileGenerator(mapper, sessions, storage, coordinator, properties).generate(job);

        Path actual = tempDir.resolve(generated.storagePath());
        assertThat(actual).isRegularFile();
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(actual))) {
            assertThat(workbook.getSheet("요약")).isNotNull();
        }
        verify(coordinator, atLeast(2)).renew(31L, "claim-a");
    }

    @Test
    void summaryOnlyPdfEmbedsBundledKoreanFontAndHeartbeatsClaim() throws Exception {
        ExportMapper mapper = mock(ExportMapper.class);
        SqlSessionFactory sessions = mock(SqlSessionFactory.class);
        ExportJobCoordinator coordinator = mock(ExportJobCoordinator.class);
        ExportProperties properties = new ExportProperties();
        properties.setRootDir(tempDir.toString());
        when(mapper.findSummaryCounts(anyLong(), any(), any(), any())).thenReturn(List.of());
        ExportJobRow job = job(ExportFormat.PDF, "[\"REPORT_SUMMARY\"]");

        var generated = new ExportFileGenerator(mapper, sessions, new ExportStorage(properties), coordinator, properties).generate(job);

        byte[] prefix = Files.readAllBytes(tempDir.resolve(generated.storagePath()));
        assertThat(new String(prefix, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(prefix.length).isGreaterThan(1_000);
        verify(coordinator, atLeast(2)).renew(31L, "claim-a");
    }

    @Test
    void generationRechecksRowLimitAfterRequestWasQueued() {
        ExportMapper mapper = mock(ExportMapper.class);
        ExportJobCoordinator coordinator = mock(ExportJobCoordinator.class);
        ExportProperties properties = new ExportProperties();
        properties.setRootDir(tempDir.toString());
        properties.setPdfMaxRows(5);
        when(mapper.countRows(eq(ExportScope.WORK), anyLong(), any(), any(), any())).thenReturn(6L);
        ExportJobRow job = job(ExportFormat.PDF, "[\"WORK\"]");

        assertThatThrownBy(() -> new ExportFileGenerator(mapper, mock(SqlSessionFactory.class),
                new ExportStorage(properties), coordinator, properties).generate(job))
                .isInstanceOfSatisfying(ExportGenerationException.class,
                        ex -> assertThat(ex.code()).isEqualTo("ROW_LIMIT_EXCEEDED"));
    }

    @Test
    void streamingBudgetRejectsRowsInsertedAfterPreflightCount() {
        var budget = new ExportFileGenerator.GenerationBudget(1);
        budget.row();

        assertThatThrownBy(budget::row)
                .isInstanceOfSatisfying(ExportGenerationException.class,
                        ex -> assertThat(ex.code()).isEqualTo("ROW_LIMIT_EXCEEDED"));
    }

    private ExportJobRow job(ExportFormat format, String scopes) {
        ExportJobRow row = new ExportJobRow();
        row.setId(31L); row.setFarmId(11L); row.setExportType(format.name()); row.setScopesJson(scopes);
        row.setConfirmationFilter("ALL"); row.setPeriodStart(LocalDate.of(2026, 7, 1));
        row.setPeriodEnd(LocalDate.of(2026, 7, 31)); row.setClaimToken("claim-a");
        return row;
    }
}
