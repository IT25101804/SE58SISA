package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.report.ReportType;
import sisa.repository.*;
import sisa.repository.*;
import sisa.service.dto.AnnouncementForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CrudCompletenessTest {

    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private AnnouncementService announcementService;
    @Autowired private SavedReportService savedReportService;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User createUser(String userId, Role role, AccountStatus status) {
        User u = new User();
        u.setUserId(userId);
        u.setUsername(userId.toLowerCase());
        u.setPassword("hashed");
        u.setFullName(userId + " Full Name");
        u.setEmail(userId + "@test.local");
        u.setRole(role);
        u.setStatus(status);
        return userRepository.save(u);
    }

    private Student createStudent(String studentId, AccountStatus status) {
        Student student = new Student();
        student.setStudentId(studentId);
        student.setUser(createUser(studentId, Role.STUDENT, status));
        student.setClassName("CRUD-Test-Class");
        student.setStatus(StudentStatus.ACTIVE);
        return studentRepository.save(student);
    }

    private User principal() {
        return userRepository.findByUsername("principal").orElseThrow();
    }

    @Test
    void principalCanDeleteRejectedTeacherAccount() {
        User t = createUser("TX900001", Role.TEACHER, AccountStatus.REJECTED);
        Teacher teacher = new Teacher();
        teacher.setTeacherId(t.getUserId());
        teacher.setUser(t);
        teacherRepository.save(teacher);

        accountDeletionService.deleteAccount("TX900001", principal());

        assertThat(userRepository.findById("TX900001")).isEmpty();
        assertThat(teacherRepository.findById("TX900001")).isEmpty();
    }

    @Test
    void approvedAccountMustBeDisabledBeforeDelete() {
        createUser("RX900001", Role.REGISTRAR, AccountStatus.APPROVED);
        assertThatThrownBy(() -> accountDeletionService.deleteAccount("RX900001", principal()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disable it first");
    }

    @Test
    void principalAccountCanNeverBeDeleted() {
        assertThatThrownBy(() -> accountDeletionService.deleteAccount(principal().getUserId(), principal()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void studentRegisteredInErrorCanBeDeleted() {
        createStudent("SX900001", AccountStatus.PENDING);
        accountDeletionService.deleteStudent("SX900001", principal());
        assertThat(studentRepository.findById("SX900001")).isEmpty();
        assertThat(userRepository.findById("SX900001")).isEmpty();
    }

    @Test
    void studentWithAttendanceCannotBeDeleted() {
        Student student = createStudent("SX900002", AccountStatus.APPROVED);
        AttendanceRecord record = new AttendanceRecord();
        record.setStudent(student);
        record.setClassName(student.getClassName());
        record.setAttendanceDate(LocalDate.now());
        record.setStatus(AttendanceStatus.PRESENT);
        record.setMarkedBy(principal().getUserId());
        record.setMarkedAt(java.time.LocalDateTime.now());
        attendanceRecordRepository.save(record);

        assertThatThrownBy(() -> accountDeletionService.deleteStudent("SX900002", principal()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archive the student instead");
        assertThat(studentRepository.findById("SX900002")).isPresent();
    }

    @Test
    void editingABroadcastUpdatesEveryRecipientAndInboxDeleteOnlyRemovesOwnCopy() {
        Student a = createStudent("SX900003", AccountStatus.APPROVED);
        Student b = createStudent("SX900004", AccountStatus.APPROVED);

        AnnouncementForm form = new AnnouncementForm();
        form.setCategory("ANNOUNCEMENT");
        form.setSubject("Sports day");
        form.setBody("Sports day is on Friday.");
        form.setTargetScope("CLASS");
        form.setClassName("CRUD-Test-Class");
        announcementService.create(form, principal());

        List<Notification> rowsA = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(a.getStudentId());
        String broadcastId = rowsA.get(0).getBroadcastId();

        announcementService.updateBroadcast(broadcastId, "Sports day (moved)", "Sports day is now on Monday.", principal());
        assertThat(notificationRepository.findByBroadcastId(broadcastId))
                .allSatisfy(n -> assertThat(n.getBody()).isEqualTo("Sports day is now on Monday."));

        User studentA = a.getUser();
        announcementService.deleteFromInbox(rowsA.get(0).getId(), studentA);
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(a.getStudentId())).isEmpty();
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(b.getStudentId())).hasSize(1);

        Long bRowId = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(b.getStudentId()).get(0).getId();
        assertThatThrownBy(() -> announcementService.deleteFromInbox(bRowId, studentA))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> announcementService.updateBroadcast(broadcastId, "x", "y", studentA))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void savedReportFullCrud() {
        User principal = principal();
        SavedReport saved = savedReportService.create("Term 1 enrolment", ReportType.ENROLMENT,
                "2026-01-01", "2026-04-30", null, principal);
        assertThat(savedReportService.listFor(principal)).extracting(SavedReport::getName).contains("Term 1 enrolment");

        savedReportService.update(saved.getId(), "Term 1 attendance", ReportType.ATTENDANCE, null, null, null, principal);
        SavedReport reloaded = savedReportService.getOwnedOrThrow(saved.getId(), principal);
        assertThat(reloaded.getName()).isEqualTo("Term 1 attendance");
        assertThat(reloaded.getReportType()).isEqualTo(ReportType.ATTENDANCE);
        assertThat(reloaded.getFromDate()).isNull();

        savedReportService.delete(saved.getId(), principal);
        assertThat(savedReportService.listFor(principal)).extracting(SavedReport::getId).doesNotContain(saved.getId());
    }

    @Test
    void registrarCannotSaveARestrictedReportType() {
        User registrar = createUser("RX900002", Role.REGISTRAR, AccountStatus.APPROVED);
        assertThatThrownBy(() -> savedReportService.create("Pay", ReportType.STAFF_PAY, null, null, null, registrar))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void savedReportsArePrivateToTheirOwner() {
        User registrar = createUser("RX900003", Role.REGISTRAR, AccountStatus.APPROVED);
        SavedReport saved = savedReportService.create("Mine", ReportType.CLASS_LIST, null, null, null, principal());
        assertThatThrownBy(() -> savedReportService.delete(saved.getId(), registrar))
                .isInstanceOf(ResponseStatusException.class);
    }
}
