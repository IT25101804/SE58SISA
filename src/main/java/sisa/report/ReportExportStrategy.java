package sisa.report;

public interface ReportExportStrategy {

    byte[] export(ReportData data);

    String contentType();

    String fileExtension();
}
