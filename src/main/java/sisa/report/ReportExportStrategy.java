package sisa.report;

/**
 * The Strategy pattern named in report section 4.5: lets ReportExportController switch
 * export formats without changing any reporting code. PdfExportStrategy and
 * ExcelExportStrategy are the two concrete strategies; ReportExportService picks one at
 * request time by format name.
 */
public interface ReportExportStrategy {

    byte[] export(ReportData data);

    String contentType();

    /** No leading dot, e.g. "pdf" or "xlsx". */
    String fileExtension();
}
