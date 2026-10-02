package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AnnouncementForm {
    private String category;
    private String subject;
    private String body;
    private String targetScope;
    private String className;
    private String studentId;
    private String teacherId;
    private String scheduledFor;
}
