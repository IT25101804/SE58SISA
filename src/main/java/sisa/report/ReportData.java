package sisa.report;

import java.util.List;

public record ReportData(String title, List<String> columns, List<List<String>> rows) {
}
