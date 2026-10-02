package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TeacherAssignmentRequest {
    private String subjectSpecialty;
    private String joiningYear;
    private boolean classTeacher;
    private String assignedClassName;
}
