package sisa.controller;

import sisa.entity.*;
import sisa.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import sisa.entity.*;
import sisa.repository.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the business rules unique to Module 3 (report FR-05/FR-06, section 6.4,
 * section 9.1 TC-05): parent notification via the Observer pattern, Subject-Teacher
 * lockout from marking, and the audit trail on same-day edits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeacherAttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ParentRepository parentRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private User createUser(String userId, String username, Role role) {
        User u = new User();
        u.setUserId(userId);
        u.setUsername(username);
        u.setPassword("hashed");
        u.setFullName(username + " Full Name");
        u.setEmail(username + "@test.local");
        u.setRole(role);
        u.setStatus(AccountStatus.APPROVED);
        return userRepository.save(u);
    }

    private Student studentIn(String studentId, String username, String className, User parentUser) {
        User studentUser = createUser(studentId, username, Role.STUDENT);
        Student student = new Student();
        student.setStudentId(studentId);
        student.setUser(studentUser);
        student.setClassName(className);
        student.setStatus(StudentStatus.ACTIVE);
        student.setParent(parentUser);
        return studentRepository.save(student);
    }

    private Teacher teacherFor(String teacherId, String username, String className, boolean classTeacher) {
        User teacherUser = createUser(teacherId, username, Role.TEACHER);
        Teacher teacher = new Teacher();
        teacher.setTeacherId(teacherId);
        teacher.setUser(teacherUser);
        teacher.setClassTeacher(classTeacher);
        teacher.setAssignedClassName(className);
        return teacherRepository.save(teacher);
    }

    private User parentFor(String parentId, String username) {
        User parentUser = createUser(parentId, username, Role.PARENT);
        Parent parent = new Parent();
        parent.setParentId(parentId);
        parent.setUser(parentUser);
        parentRepository.save(parent);
        return parentUser;
    }

    @Test
    void markingAbsentCreatesNotificationForLinkedParentWithinTheSameRequest() throws Exception {
        User parentUser = parentFor("P2699101", "parent101");
        studentIn("S2699101", "student101", "5A", parentUser);
        teacherFor("T2699101", "classteacher101", "5A", true);

        mockMvc.perform(post("/teacher/attendance/mark")
                        .with(user("classteacher101").roles("TEACHER"))
                        .with(csrf())
                        .param("className", "5A")
                        .param("entries[0].studentId", "S2699101")
                        .param("entries[0].status", "ABSENT"))
                .andExpect(status().is3xxRedirection());

        List<Notification> notifications = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc("P2699101");
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getBody()).contains("ABSENT");
    }

    @Test
    void subjectTeacherCannotPostToMarkAttendanceEndpoint() throws Exception {
        studentIn("S2699102", "student102", "6B", null);
        teacherFor("T2699102", "subjectteacher102", "6B", false); // subject teacher, NOT the class teacher

        mockMvc.perform(post("/teacher/attendance/mark")
                        .with(user("subjectteacher102").roles("TEACHER"))
                        .with(csrf())
                        .param("className", "6B")
                        .param("entries[0].studentId", "S2699102")
                        .param("entries[0].status", "PRESENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editingTodaysAttendanceUpdatesRecordAndWritesAuditTrail() throws Exception {
        studentIn("S2699103", "student103", "7C", null);
        teacherFor("T2699103", "classteacher103", "7C", true);

        mockMvc.perform(post("/teacher/attendance/mark")
                        .with(user("classteacher103").roles("TEACHER"))
                        .with(csrf())
                        .param("className", "7C")
                        .param("entries[0].studentId", "S2699103")
                        .param("entries[0].status", "PRESENT"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/teacher/attendance/mark")
                        .with(user("classteacher103").roles("TEACHER"))
                        .with(csrf())
                        .param("className", "7C")
                        .param("entries[0].studentId", "S2699103")
                        .param("entries[0].status", "ABSENT"))
                .andExpect(status().is3xxRedirection());

        AttendanceRecord record = attendanceRecordRepository
                .findByStudent_StudentIdAndAttendanceDate("S2699103", LocalDate.now())
                .orElseThrow();
        assertThat(record.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(record.getLastEditedBy()).isEqualTo("T2699103");
        assertThat(record.getLastEditedAt()).isNotNull();

        List<AuditLogEntry> audit = auditLogRepository.findByUserIdOrderByTimestampDesc("S2699103");
        assertThat(audit).anyMatch(e -> e.getAction().equals("EDIT_ATTENDANCE") && e.getPerformedByUserId().equals("T2699103"));
    }
}
