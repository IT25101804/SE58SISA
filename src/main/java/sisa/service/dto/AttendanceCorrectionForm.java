package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttendanceCorrectionForm {
    private Long attendanceRecordId;
    private String requestedStatus;
    private String reason;
}
