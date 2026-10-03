package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ExamForm {
    private String className;
    private String subject;
    private String examName;
    private String examDate;
    private double maxMarks = 100;
    private Long termId;
}
