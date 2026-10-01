package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TimetableSlotForm {
    private String className;
    private String subject;
    private String teacherId;
    private String dayOfWeek; // MONDAY..FRIDAY
    private int periodNumber;
    private Long roomResourceId; // Module 8 Resource id, optional
}
