package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A Student's request to fix one of their own AttendanceRecord rows (business rule 5). */
@Getter
@Setter
@NoArgsConstructor
public class AttendanceCorrectionForm {
    private Long attendanceRecordId;
    private String requestedStatus; // PRESENT / ABSENT / LATE
    private String reason;
}
