package sisa.report;

import java.util.List;

/**
 * A generic, export-agnostic report: a title, column headers, and rows of cells.
 * Every report type (enrolment, attendance, academic, staff, ...) is built down to
 * this one shape, so the on-screen table, the PDF strategy and the Excel strategy all
 * render the exact same data — see ReportExportStrategy.
 */
public record ReportData(String title, List<String> columns, List<List<String>> rows) {
}
