package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GradeForm {
    private Long submissionId;
    private Double marks;
    private String grade;
    private String feedback;
}
