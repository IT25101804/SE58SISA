package sisa.report;

/**
 * The report picker's options (business rules 1 & 4). Principal may request any of
 * these; Registrar is restricted server-side to the partial subset named in report
 * section 6.3 — ENROLMENT (covers "enrolment/admission"), TRANSFER, CLASS_LIST and
 * STAFF (a plain directory, not the performance-enriched Principal version) — see
 * RegistrarReportingController.
 */
public enum ReportType {
    ENROLMENT,
    ATTENDANCE,
    ACADEMIC,
    STAFF,
    STAFF_PAY,
    TRANSFER,
    CLASS_LIST
}
