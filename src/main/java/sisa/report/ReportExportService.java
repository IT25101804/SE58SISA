package sisa.report;

import org.springframework.stereotype.Service;

import java.util.Map;

/** Picks the right ReportExportStrategy by format name at request time (the Strategy pattern's "context"). */
@Service
public class ReportExportService {

    private final Map<String, ReportExportStrategy> strategiesByFormat;

    public ReportExportService(PdfExportStrategy pdfExportStrategy, ExcelExportStrategy excelExportStrategy) {
        this.strategiesByFormat = Map.of(
                "pdf", pdfExportStrategy,
                "excel", excelExportStrategy,
                "xlsx", excelExportStrategy);
    }

    public ReportExportStrategy strategyFor(String format) {
        ReportExportStrategy strategy = strategiesByFormat.get(format == null ? "" : format.toLowerCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown export format: " + format + " (use pdf or excel)");
        }
        return strategy;
    }
}
