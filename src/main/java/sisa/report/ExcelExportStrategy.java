package sisa.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Concrete Strategy: renders a ReportData as a simple one-sheet XLSX (Apache POI). */
@Component
public class ExcelExportStrategy implements ReportExportStrategy {

    @Override
    public byte[] export(ReportData data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(data.title()));

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(data.title());

            Row headerRow = sheet.createRow(2);
            List<String> columns = data.columns();
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(columns.get(c));
                cell.setCellStyle(headerStyle);
            }

            List<List<String>> rows = data.rows();
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 3);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    row.createCell(c).setCellValue(cells.get(c) == null ? "" : cells.get(c));
                }
            }

            for (int c = 0; c < Math.max(1, columns.size()); c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Excel report", e);
        }
    }

    private String safeSheetName(String title) {
        String name = title == null || title.isBlank() ? "Report" : title;
        name = name.replaceAll("[\\\\/*\\[\\]:?]", " ").trim();
        return name.length() > 31 ? name.substring(0, 31) : name;
    }

    @Override
    public String contentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public String fileExtension() {
        return "xlsx";
    }
}
