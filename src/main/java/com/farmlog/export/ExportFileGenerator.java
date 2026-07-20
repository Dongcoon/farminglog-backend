package com.farmlog.export;

import com.farmlog.export.entity.ExportJobRow;
import com.farmlog.export.entity.ExportRecordRow;
import com.farmlog.export.entity.GeneratedFile;
import com.farmlog.export.mapper.ExportMapper;
import com.farmlog.report.entity.AveragePriceRow;
import com.farmlog.report.entity.RecordCountRow;
import com.farmlog.report.entity.SalesComparisonRow;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 메모리 사용량을 제한하면서 XLSX/PDF를 생성하고 생성 도중 lease를 주기적으로 갱신한다. */
@Component
public class ExportFileGenerator {
    private static final String FONT_RESOURCE = "/fonts/NotoSansKR-Regular.ttf";
    private static final int HEARTBEAT_ROWS = 250;
    private static final long HEARTBEAT_NANOS = 30_000_000_000L;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExportMapper mapper;
    private final SqlSessionFactory sqlSessionFactory;
    private final ExportStorage storage;
    private final ExportJobCoordinator coordinator;
    private final ExportProperties properties;

    public ExportFileGenerator(ExportMapper mapper, SqlSessionFactory sqlSessionFactory, ExportStorage storage,
                               ExportJobCoordinator coordinator, ExportProperties properties) {
        this.mapper = mapper;
        this.sqlSessionFactory = sqlSessionFactory;
        this.storage = storage;
        this.coordinator = coordinator;
        this.properties = properties;
    }

    public GeneratedFile generate(ExportJobRow job) {
        ExportFormat format = ExportFormat.valueOf(job.getExportType());
        String extension = format.name().toLowerCase();
        ExportStorage.PendingFile pending = null;
        try {
            pending = storage.pending(job.getFarmId(), extension);
            Heartbeat heartbeat = new Heartbeat(job);
            validateCurrentRowLimit(job, format, heartbeat);
            GenerationBudget budget = new GenerationBudget(rowLimit(format));
            if (format == ExportFormat.XLSX) generateXlsx(job, pending.tempPath(), heartbeat, budget);
            else generatePdf(job, pending.tempPath(), heartbeat, budget);
            heartbeat.renew(true);
            storage.publish(pending);
            long size = Files.size(pending.finalPath());
            String contentType = format == ExportFormat.XLSX
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/pdf";
            return new GeneratedFile(originalName(job, extension), pending.storedFileName(), contentType, size,
                    Path.of(pending.relativePath()));
        } catch (StaleExportClaimException ex) {
            cleanup(pending);
            throw ex;
        } catch (IOException ex) {
            cleanup(pending);
            throw new ExportGenerationException("STORAGE_UNAVAILABLE", "파일 저장소를 사용할 수 없습니다.", true, ex);
        } catch (RuntimeException ex) {
            cleanup(pending);
            if (ex instanceof ExportGenerationException generation) throw generation;
            throw new ExportGenerationException("GENERATION_FAILED", "파일 생성에 실패했습니다. 다시 요청해 주세요.", true, ex);
        }
    }

    /** 요청 이후 기록이 늘어날 수 있으므로 생성 직전에도 행수 한도를 다시 검증한다. */
    private void validateCurrentRowLimit(ExportJobRow job, ExportFormat format, Heartbeat heartbeat) {
        long rows = 0;
        for (ExportScope scope : scopes(job)) {
            if (scope == ExportScope.REPORT_SUMMARY) continue;
            heartbeat.renew(true);
            long scopeRows = mapper.countRows(scope, job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(),
                    ConfirmationFilter.valueOf(job.getConfirmationFilter()));
            if (Long.MAX_VALUE - rows < scopeRows) rows = Long.MAX_VALUE;
            else rows += scopeRows;
        }
        long max = rowLimit(format);
        if (rows > max) {
            throw new ExportGenerationException("ROW_LIMIT_EXCEEDED",
                    "기록이 너무 많습니다. 기간이나 포함 범위를 줄여 주세요.", false, null);
        }
    }

    private long rowLimit(ExportFormat format) {
        return format == ExportFormat.XLSX ? properties.getXlsxMaxRows() : properties.getPdfMaxRows();
    }

    private void generateXlsx(ExportJobRow job, Path path, Heartbeat heartbeat,
                              GenerationBudget budget) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); OutputStream output = Files.newOutputStream(path)) {
            workbook.setCompressTempFiles(true);
            CellStyle headerStyle = headerStyle(workbook);
            for (ExportScope scope : scopes(job)) {
                heartbeat.renew(true);
                if (scope == ExportScope.REPORT_SUMMARY) writeSummarySheet(workbook, job, headerStyle);
                else writeRecordSheet(workbook, job, scope, headerStyle, heartbeat, budget);
            }
            workbook.write(output);
        }
    }

    private void writeRecordSheet(SXSSFWorkbook workbook, ExportJobRow job, ExportScope scope,
                                  CellStyle headerStyle, Heartbeat heartbeat, GenerationBudget budget) throws IOException {
        Sheet sheet = workbook.createSheet(sheetName(scope));
        List<String> headers = headers(scope);
        writeRow(sheet.createRow(0), headers, headerStyle);
        int rowNumber = 1;
        // Mapper cursor는 생성이 끝날 때까지 열린 전용 read session에 묶는다.
        try (SqlSession session = sqlSessionFactory.openSession();
             Cursor<ExportRecordRow> cursor = session.getMapper(ExportMapper.class).streamRecords(scope, job.getFarmId(),
                     job.getPeriodStart(), job.getPeriodEnd(), ConfirmationFilter.valueOf(job.getConfirmationFilter()))) {
            for (ExportRecordRow record : cursor) {
                // 생성 직전 COUNT 이후 들어온 행도 실제 출력 시점에 다시 제한한다.
                budget.row();
                writeRow(sheet.createRow(rowNumber++), values(scope, record), null);
                heartbeat.row();
            }
        }
        sheet.createFreezePane(0, 1);
        for (int i = 0; i < headers.size(); i++) sheet.setColumnWidth(i, Math.min(40, Math.max(12, headers.get(i).length() + 4)) * 256);
    }

    private void writeSummarySheet(SXSSFWorkbook workbook, ExportJobRow job, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(sheetName(ExportScope.REPORT_SUMMARY));
        int row = 0;
        writeRow(sheet.createRow(row++), List.of("항목", "값1", "값2", "값3"), headerStyle);
        for (RecordCountRow count : mapper.findSummaryCounts(job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(),
                ConfirmationFilter.valueOf(job.getConfirmationFilter()))) {
            writeRow(sheet.createRow(row++), List.of("기록 수", count.getRecordType(), count.getRecordCount(), ""), null);
        }
        for (AveragePriceRow harvest : mapper.findSummaryHarvest(job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(),
                ConfirmationFilter.valueOf(job.getConfirmationFilter()))) {
            writeRow(sheet.createRow(row++), List.of("수확량", harvest.getUnit(), harvest.getSoldQuantity(), ""), null);
        }
        SalesComparisonRow sales = mapper.findSummarySales(job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(),
                ConfirmationFilter.valueOf(job.getConfirmationFilter()));
        if (sales != null) {
            writeRow(sheet.createRow(row++), List.of("판매 합계", sales.getCurrentGross(), sales.getCurrentFee(), sales.getCurrentNet()), null);
        }
        for (AveragePriceRow average : mapper.findSummaryAveragePrices(job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(),
                ConfirmationFilter.valueOf(job.getConfirmationFilter()))) {
            BigDecimal price = average.getSoldQuantity() == null || average.getSoldQuantity().signum() == 0
                    ? BigDecimal.ZERO : average.getGrossSalesAmount().divide(average.getSoldQuantity(), 2, java.math.RoundingMode.HALF_UP);
            writeRow(sheet.createRow(row++), List.of("평균 단가", average.getUnit(), average.getSoldQuantity(), price), null);
        }
        sheet.createFreezePane(0, 1);
        for (int i = 0; i < 4; i++) sheet.setColumnWidth(i, 20 * 256);
    }

    private void generatePdf(ExportJobRow job, Path path, Heartbeat heartbeat,
                             GenerationBudget budget) throws IOException {
        if (getClass().getResource(FONT_RESOURCE) == null) {
            throw new ExportGenerationException("FONT_UNAVAILABLE", "PDF 한글 글꼴을 준비하지 못했습니다.", false, null);
        }
        String html = buildPdfHtml(job, heartbeat, budget);
        try (OutputStream output = Files.newOutputStream(path)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(this::openFont, "Noto Sans KR");
            builder.withHtmlContent(html, null);
            builder.toStream(output);
            builder.run();
        }
    }

    private String buildPdfHtml(ExportJobRow job, Heartbeat heartbeat, GenerationBudget budget) throws IOException {
        StringBuilder html = new StringBuilder(32_768);
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>")
                .append("@page{size:A4 landscape;margin:12mm}body{font-family:'Noto Sans KR';font-size:8px;color:#222}")
                .append("h1{font-size:16px}h2{font-size:12px;margin-top:16px}table{width:100%;border-collapse:collapse;table-layout:fixed}")
                .append("th,td{border:1px solid #bbb;padding:3px;overflow-wrap:anywhere}th{background:#e8f3e8}</style></head><body>")
                .append("<h1>파밍로그 내보내기</h1><p>기간: ").append(job.getPeriodStart()).append(" ~ ")
                .append(job.getPeriodEnd()).append("</p>");
        for (ExportScope scope : scopes(job)) {
            heartbeat.renew(true);
            if (scope == ExportScope.REPORT_SUMMARY) appendPdfSummary(html, job);
            else appendPdfRecords(html, job, scope, heartbeat, budget);
        }
        return html.append("</body></html>").toString();
    }

    private void appendPdfRecords(StringBuilder html, ExportJobRow job, ExportScope scope,
                                  Heartbeat heartbeat, GenerationBudget budget) throws IOException {
        List<String> headers = headers(scope);
        html.append("<h2>").append(escape(sheetName(scope))).append("</h2><table><thead><tr>");
        headers.forEach(header -> html.append("<th>").append(escape(header)).append("</th>"));
        html.append("</tr></thead><tbody>");
        try (SqlSession session = sqlSessionFactory.openSession();
             Cursor<ExportRecordRow> cursor = session.getMapper(ExportMapper.class).streamRecords(scope, job.getFarmId(),
                     job.getPeriodStart(), job.getPeriodEnd(), ConfirmationFilter.valueOf(job.getConfirmationFilter()))) {
            for (ExportRecordRow record : cursor) {
                budget.row();
                html.append("<tr>");
                values(scope, record).forEach(value -> html.append("<td>").append(escape(value)).append("</td>"));
                html.append("</tr>");
                heartbeat.row();
            }
        }
        html.append("</tbody></table>");
    }

    /** 여러 scope를 합친 실제 출력 행이 요청 한도를 넘지 않도록 스트리밍 중에도 강제한다. */
    static final class GenerationBudget {
        private final long maxRows;
        private long rows;

        GenerationBudget(long maxRows) { this.maxRows = maxRows; }

        void row() {
            if (++rows > maxRows) {
                throw new ExportGenerationException("ROW_LIMIT_EXCEEDED",
                        "기록이 너무 많습니다. 기간이나 포함 범위를 줄여 주세요.", false, null);
            }
        }
    }

    private void appendPdfSummary(StringBuilder html, ExportJobRow job) {
        html.append("<h2>요약</h2><table><thead><tr><th>항목</th><th>구분</th><th>값</th></tr></thead><tbody>");
        ConfirmationFilter filter = ConfirmationFilter.valueOf(job.getConfirmationFilter());
        for (RecordCountRow count : mapper.findSummaryCounts(job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(),
                filter)) {
            html.append("<tr><td>기록 수</td><td>").append(escape(count.getRecordType())).append("</td><td>")
                    .append(count.getRecordCount()).append("</td></tr>");
        }
        for (AveragePriceRow harvest : mapper.findSummaryHarvest(job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(), filter)) {
            html.append("<tr><td>수확량</td><td>").append(escape(harvest.getUnit())).append("</td><td>")
                    .append(escape(harvest.getSoldQuantity())).append("</td></tr>");
        }
        SalesComparisonRow sales = mapper.findSummarySales(job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(), filter);
        if (sales != null) {
            html.append("<tr><td>총 판매액</td><td>금액</td><td>").append(escape(sales.getCurrentGross())).append("</td></tr>")
                    .append("<tr><td>수수료</td><td>금액</td><td>").append(escape(sales.getCurrentFee())).append("</td></tr>")
                    .append("<tr><td>순 판매액</td><td>금액</td><td>").append(escape(sales.getCurrentNet())).append("</td></tr>");
        }
        for (AveragePriceRow average : mapper.findSummaryAveragePrices(job.getFarmId(), job.getPeriodStart(), job.getPeriodEnd(), filter)) {
            BigDecimal price = average.getSoldQuantity() == null || average.getSoldQuantity().signum() == 0
                    ? BigDecimal.ZERO : average.getGrossSalesAmount().divide(average.getSoldQuantity(), 2, java.math.RoundingMode.HALF_UP);
            html.append("<tr><td>평균 단가</td><td>").append(escape(average.getUnit())).append("</td><td>")
                    .append(escape(price)).append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    private InputStream openFont() {
        InputStream stream = getClass().getResourceAsStream(FONT_RESOURCE);
        if (stream == null) throw new IllegalStateException("Bundled PDF font is missing");
        return stream;
    }

    private List<ExportScope> scopes(ExportJobRow job) {
        try {
            ExportScope[] values = new com.fasterxml.jackson.databind.ObjectMapper().readValue(job.getScopesJson(), ExportScope[].class);
            return List.of(values);
        } catch (Exception ex) {
            throw new ExportGenerationException("INVALID_JOB", "내보내기 요청 정보가 올바르지 않습니다.", false, ex);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeRow(Row row, List<?> values, CellStyle style) {
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            Object value = values.get(i);
            if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else if (value != null) cell.setCellValue(value.toString());
            if (style != null) cell.setCellStyle(style);
        }
    }

    private List<String> headers(ExportScope scope) {
        List<String> common = List.of("ID", "기록일", "구역", "작물", "품종", "작기");
        List<String> specific = switch (scope) {
            case WORK -> List.of("작업 유형", "작업 인원", "작업 시간");
            case PEST_CONTROL -> List.of("약제명", "대상 병해충", "희석 배수", "사용량", "단위", "수확 전 안전일");
            case HARVEST -> List.of("등급", "수확량", "단위", "포장 단위");
            case SALES -> List.of("거래처", "품목", "수량", "단위", "단가", "총 판매액", "수수료", "순 판매액", "정산 상태");
            case REPORT_SUMMARY -> List.of();
        };
        List<String> result = new ArrayList<>(common);
        result.addAll(specific);
        result.addAll(List.of("메모", "확인 상태", "작성자", "작성 시각"));
        return result;
    }

    private List<Object> values(ExportScope scope, ExportRecordRow row) {
        List<Object> result = new ArrayList<>(List.of(value(row.getId()), value(row.getRecordDate()), value(row.getZoneName()),
                value(row.getCropName()), value(row.getVarietyName()), value(row.getSeasonName())));
        switch (scope) {
            case WORK -> result.addAll(List.of(value(row.getWorkTypeName()), value(row.getWorkerCount()), value(row.getWorkHours())));
            case PEST_CONTROL -> result.addAll(List.of(value(row.getChemicalName()), value(row.getTargetPest()),
                    value(row.getDilutionRatio()), value(row.getAmountValue()), value(row.getAmountUnit()), value(row.getPreharvestIntervalDays())));
            case HARVEST -> result.addAll(List.of(value(row.getGrade()), value(row.getQuantity()), value(row.getUnit()), value(row.getPackageUnit())));
            case SALES -> result.addAll(List.of(value(row.getCustomerName()), value(row.getItemName()), value(row.getQuantity()),
                    value(row.getUnit()), value(row.getUnitPrice()), value(row.getGrossAmount()), value(row.getFeeAmount()),
                    value(row.getNetAmount()), value(row.getSettlementStatus())));
            case REPORT_SUMMARY -> { }
        }
        result.addAll(List.of(value(row.getMemo()), value(row.getFarmerConfirmStatus()), value(row.getCreatedByName()), value(row.getCreatedAt())));
        return result;
    }

    private Object value(Object value) {
        return value == null ? "" : value;
    }

    private String sheetName(ExportScope scope) {
        return switch (scope) {
            case WORK -> "작업 기록";
            case PEST_CONTROL -> "방제 기록";
            case HARVEST -> "수확 기록";
            case SALES -> "판매 기록";
            case REPORT_SUMMARY -> "요약";
        };
    }

    private String originalName(ExportJobRow job, String extension) {
        return "파밍로그_" + job.getFarmId() + "_" + FILE_DATE.format(job.getPeriodStart()) + "_"
                + FILE_DATE.format(job.getPeriodEnd()) + "." + extension;
    }

    private String escape(Object raw) {
        if (raw == null) return "";
        return raw.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void cleanup(ExportStorage.PendingFile pending) {
        if (pending == null) return;
        try {
            Files.deleteIfExists(pending.tempPath());
            Files.deleteIfExists(pending.finalPath());
        } catch (IOException ignored) {
            // 정리 실패는 원래 생성 오류를 가리지 않으며, exports 경로만 대상으로 운영 정리할 수 있다.
        }
    }

    private final class Heartbeat {
        private final ExportJobRow job;
        private int rows;
        private long lastRenewed = System.nanoTime();

        private Heartbeat(ExportJobRow job) {
            this.job = job;
        }

        private void row() {
            rows++;
            renew(rows % HEARTBEAT_ROWS == 0 || System.nanoTime() - lastRenewed >= HEARTBEAT_NANOS);
        }

        private void renew(boolean due) {
            if (!due) return;
            coordinator.renew(job.getId(), job.getClaimToken());
            lastRenewed = System.nanoTime();
        }
    }
}
