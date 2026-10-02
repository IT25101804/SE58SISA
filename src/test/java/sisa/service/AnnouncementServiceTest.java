package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.NotificationRepository;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.UserRepository;
import sisa.service.dto.AnnouncementForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AnnouncementServiceTest {

    @Autowired
    private AnnouncementService announcementService;
    @Autowired
    private NotificationSchedulerService notificationSchedulerService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeacherRepository teacherRepository;

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

    private User createParent(String parentId, String username) {
        return createUser(parentId, username, Role.PARENT);
    }

    private Student createStudent(String studentId, String username, String className, User parentUser) {
        User studentUser = createUser(studentId, username, Role.STUDENT);
        Student student = new Student();
        student.setStudentId(studentId);
        student.setUser(studentUser);
        student.setClassName(className);
        student.setStatus(StudentStatus.ACTIVE);
        student.setParent(parentUser);
        return studentRepository.save(student);
    }

    private User principal() {
        return userRepository.findByUsername("principal").orElseThrow();
    }

    private AnnouncementForm baseForm(String category, String scope) {
        AnnouncementForm form = new AnnouncementForm();
        form.setCategory(category);
        form.setSubject("Test Subject");
        form.setBody("Test body.");
        form.setTargetScope(scope);
        return form;
    }

    @Test
    void wholeSchoolAnnouncementReachesEveryEnabledUser() {
        User teacher = createUser("T2699401", "teacher401", Role.TEACHER);
        User student = createUser("S2699401", "student401", Role.STUDENT);
        User parent = createUser("P2699401", "parent401", Role.PARENT);
        User disabled = createUser("S2699402", "student402", Role.STUDENT);
        disabled.setStatus(AccountStatus.DISABLED);
        userRepository.save(disabled);

        AnnouncementForm form = baseForm("ANNOUNCEMENT", "SCHOOL");
        int reached = announcementService.create(form, principal());

        List<Notification> all = notificationRepository.findAll();
        List<String> recipientIds = all.stream().map(Notification::getRecipientUserId).toList();

        assertThat(recipientIds).contains(principal().getUserId(), teacher.getUserId(), student.getUserId(), parent.getUserId());
        assertThat(recipientIds).doesNotContain(disabled.getUserId());
        assertThat(reached).isEqualTo(recipientIds.size());
    }

    @Test
    void classAnnouncementReachesOnlyThatClasssStudentsAndTheirParents() {
        User parentA = createParent("P2699403", "parentA403");
        Student studentA1 = createStudent("S2699403", "studentA1_403", "9A", parentA);
        Student studentA2 = createStudent("S2699404", "studentA2_404", "9A", parentA);

        User parentB = createParent("P2699405", "parentB405");
        Student studentB = createStudent("S2699405", "studentB405", "9B", parentB);

        User unrelatedTeacher = createUser("T2699405", "teacher405", Role.TEACHER);

        AnnouncementForm form = baseForm("ANNOUNCEMENT", "CLASS");
        form.setClassName("9A");
        int reached = announcementService.create(form, principal());

        List<String> recipientIds = notificationRepository.findAll().stream()
                .filter(n -> "Test Subject".equals(n.getSubject()))
                .map(Notification::getRecipientUserId).toList();

        assertThat(reached).isEqualTo(3);
        assertThat(recipientIds).containsExactlyInAnyOrder(
                studentA1.getStudentId(), studentA2.getStudentId(), parentA.getUserId());
        assertThat(recipientIds).doesNotContain(studentB.getStudentId(), parentB.getUserId(), unrelatedTeacher.getUserId());
    }

    @Test
    void scheduledAnnouncementIsNotVisibleUntilDue() {
        Student student = createStudent("S2699407", "student407", "9C", null);

        AnnouncementForm futureForm = baseForm("REMINDER", "STUDENT");
        futureForm.setStudentId(student.getStudentId());
        futureForm.setScheduledFor(LocalDateTime.now().plusHours(2).toString());
        announcementService.create(futureForm, principal());

        List<Notification> visibleBeforeDue =
                notificationRepository.findByRecipientUserIdAndSentAtIsNotNullOrderBySentAtDesc(student.getStudentId());
        assertThat(visibleBeforeDue).isEmpty();

        List<Notification> allForStudent = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(student.getStudentId());
        assertThat(allForStudent).hasSize(1);

        Notification pending = allForStudent.get(0);
        pending.setScheduledFor(LocalDateTime.now().minusMinutes(1));
        notificationRepository.save(pending);

        int flipped = notificationSchedulerService.flipDueToSent();
        assertThat(flipped).isGreaterThanOrEqualTo(1);

        List<Notification> visibleAfterDue =
                notificationRepository.findByRecipientUserIdAndSentAtIsNotNullOrderBySentAtDesc(student.getStudentId());
        assertThat(visibleAfterDue).hasSize(1);
        assertThat(visibleAfterDue.get(0).getScheduledFor()).isBefore(LocalDateTime.now());
    }
}
