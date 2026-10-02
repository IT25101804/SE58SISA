package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AnnouncementForm {
    private String category;    // ANNOUNCEMENT / ALERT / REMINDER
    private String subject;
    private String body;
    private String targetScope; // SCHOOL / CLASS / STUDENT
    private String className;   // used when targetScope == CLASS
    private String studentId;   // used when targetScope == STUDENT / ONE_STUDENT / GUARDIANS
    private String teacherId;   // used when targetScope == TEACHER
    private String scheduledFor; // yyyy-MM-ddTHH:mm from <input type="datetime-local">, optional
}
