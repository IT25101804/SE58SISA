package sisa.report;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Strategy pattern's promise (report section 4.5): the exact same ReportData
 * produces a valid file via either strategy, with no reporting code needing to know
 * which one is in play. Plain unit tests — no Spring context needed, since neither
 * strategy has any dependency.
 */
class ReportExportStrategyTest {

    private ReportData sampleData() {
        return new ReportData("Sample Report",
                List.of("Name", "Score"),
                List.of(List.of("Alice", "90"), List.of("Bob", "72")));
    }

    @Test
    void pdfStrategyProducesAValidNonEmptyPdf() {
        byte[] bytes = new PdfExportStrategy().export(sampleData());

        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isGreaterThan(100);
        String header = new String(bytes, 0, 5, StandardCharsets.ISO_8859_1);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    void excelStrategyProducesAValidNonEmptyXlsxFromTheSameData() throws IOException {
        byte[] bytes = new ExcelExportStrategy().export(sampleData());

        assertThat(bytes).isNotEmpty();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(2);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Name");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Score");

            Row firstDataRow = sheet.getRow(3);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("Alice");
            assertThat(firstDataRow.getCell(1).getStringCellValue()).isEqualTo("90");

            Row secondDataRow = sheet.getRow(4);
            assertThat(secondDataRow.getCell(0).getStringCellValue()).isEqualTo("Bob");
        }
    }

    @Test
    void bothStrategiesHandleAnEmptyReportWithoutThrowing() throws IOException {
        ReportData empty = new ReportData("Empty Report", List.of("Column"), List.of());

        byte[] pdfBytes = new PdfExportStrategy().export(empty);
        assertThat(pdfBytes).isNotEmpty();

        byte[] excelBytes = new ExcelExportStrategy().export(empty);
        assertThat(excelBytes).isNotEmpty();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excelBytes))) {
            assertThat(workbook.getSheetAt(0)).isNotNull();
        }
    }
}
