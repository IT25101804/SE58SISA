package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AssignmentForm {
    private String className;
    private String subject;
    private String title;
    private String description;
    private String materialUrl;
    private String dueDate;
}
