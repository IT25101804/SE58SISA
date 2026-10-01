package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.ResourceBookingRepository;
import sisa.repository.ResourceRepository;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.UserRepository;
import sisa.service.dto.AssignmentForm;
import sisa.service.dto.TimetableSlotForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the business rules unique to Module 4 (report FR-09/FR-10, section 9.1):
 * TC-06 (late submissions are marked, not blocked), the teacher/period double-booking
 * check (business rule 2), and that a teacher's grade + feedback is visible on the
 * student's own submission.
 */
@SpringBootTest
@Transactional
class TimetableAssignmentServiceTest {

    @Autowired
    private TimetableService timetableService;
    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private SubmissionService submissionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ResourceBookingRepository resourceBookingRepository;

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

    private Teacher createTeacher(String teacherId, String username, String assignedClassName, boolean classTeacher) {
        User teacherUser = createUser(teacherId, username, Role.TEACHER);
        Teacher teacher = new Teacher();
        teacher.setTeacherId(teacherId);
        teacher.setUser(teacherUser);
        teacher.setClassTeacher(classTeacher);
        teacher.setAssignedClassName(assignedClassName);
        return teacherRepository.save(teacher);
    }

    private Student createStudent(String studentId, String username, String className) {
        User studentUser = createUser(studentId, username, Role.STUDENT);
        Student student = new Student();
        student.setStudentId(studentId);
        student.setUser(studentUser);
        student.setClassName(className);
        student.setStatus(StudentStatus.ACTIVE);
        return studentRepository.save(student);
    }

    @Test
    void submissionAfterDueDateIsAutomaticallyMarkedLate() {
        Teacher teacher = createTeacher("T2699201", "teacher201", "5A", true);
        Student student = createStudent("S2699201", "student201", "5A");

        AssignmentForm form = new AssignmentForm();
        form.setClassName("5A");
        form.setSubject("Maths");
        form.setTitle("Homework 1");
        form.setDescription("Do the exercises");
        form.setDueDate(LocalDate.now().minusDays(1).toString()); // due yesterday

        Assignment assignment = assignmentService.create(form, teacher);

        AssignmentSubmission submission = submissionService.submit(assignment, student, "my answer");

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.LATE);
    }

    @Test
    void submissionBeforeDueDateIsMarkedOnTime() {
        Teacher teacher = createTeacher("T2699202", "teacher202", "5B", true);
        Student student = createStudent("S2699202", "student202", "5B");

        AssignmentForm form = new AssignmentForm();
        form.setClassName("5B");
        form.setSubject("Science");
        form.setTitle("Homework 2");
        form.setDueDate(LocalDate.now().plusDays(3).toString());

        Assignment assignment = assignmentService.create(form, teacher);

        AssignmentSubmission submission = submissionService.submit(assignment, student, "my answer");

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.ON_TIME);
    }

    @Test
    void assigningTheSameTeacherToTwoOverlappingPeriodsIsRejected() {
        Teacher teacher = createTeacher("T2699203", "teacher203", null, false);
        createStudent("S2699203a", "student203a", "6A");
        createStudent("S2699203b", "student203b", "6B");

        TimetableSlotForm first = new TimetableSlotForm();
        first.setClassName("6A");
        first.setSubject("English");
        first.setTeacherId(teacher.getTeacherId());
        first.setDayOfWeek("MONDAY");
        first.setPeriodNumber(1);
        timetableService.upsertSlot(first);

        TimetableSlotForm conflicting = new TimetableSlotForm();
        conflicting.setClassName("6B");
        conflicting.setSubject("History");
        conflicting.setTeacherId(teacher.getTeacherId());
        conflicting.setDayOfWeek("MONDAY");
        conflicting.setPeriodNumber(1);

        assertThatThrownBy(() -> timetableService.upsertSlot(conflicting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already teaching")
                .hasMessageContaining("6A");
    }

    @Test
    void editingTheSameSlotDoesNotConflictWithItself() {
        Teacher teacher = createTeacher("T2699204", "teacher204", null, false);
        createStudent("S2699204", "student204", "7A");

        Resource room1 = new Resource();
        room1.setName("Room 1");
        room1.setType(ResourceType.ROOM);
        resourceRepository.save(room1);
        Resource room2 = new Resource();
        room2.setName("Room 2");
        room2.setType(ResourceType.ROOM);
        resourceRepository.save(room2);

        TimetableSlotForm form = new TimetableSlotForm();
        form.setClassName("7A");
        form.setSubject("Art");
        form.setTeacherId(teacher.getTeacherId());
        form.setDayOfWeek("TUESDAY");
        form.setPeriodNumber(2);
        form.setRoomResourceId(room1.getId());
        timetableService.upsertSlot(form);

        form.setRoomResourceId(room2.getId()); // re-saving the same class/day/period — should just update, not conflict
        timetableService.upsertSlot(form);

        TimetableSlot updated = timetableService.slotsForClass("7A").get(0);
        assertThat(updated.getRoomName()).isEqualTo("Room 2");
    }

    /**
     * Module 8 integration (report section 7, item 10; business rule 4): a recurring
     * TimetableSlot and an ad-hoc ResourceBooking share one source of truth for room
     * availability, so an existing ad-hoc booking on the matching day-of-week blocks a
     * new timetable slot for that same room+period.
     */
    @Test
    void savingATimetableSlotIsRejectedWhenItsRoomIsAlreadyAdHocBookedOnThatDayOfWeek() {
        Teacher teacher = createTeacher("T2699206", "teacher206", null, false);
        createStudent("S2699206", "student206", "8B");

        Resource room = new Resource();
        room.setName("Room 30");
        room.setType(ResourceType.ROOM);
        resourceRepository.save(room);

        LocalDate bookedDate = LocalDate.of(2026, 10, 7);
        ResourceBooking booking = new ResourceBooking();
        booking.setResource(room);
        booking.setBookedByUserId("T9999999");
        booking.setBookingDate(bookedDate);
        booking.setPeriodNumber(5);
        booking.setPurpose("Science fair setup");
        booking.setStatus(BookingStatus.APPROVED);
        resourceBookingRepository.save(booking);

        TimetableSlotForm form = new TimetableSlotForm();
        form.setClassName("8B");
        form.setSubject("Geography");
        form.setTeacherId(teacher.getTeacherId());
        form.setDayOfWeek(bookedDate.getDayOfWeek().name());
        form.setPeriodNumber(5);
        form.setRoomResourceId(room.getId());

        assertThatThrownBy(() -> timetableService.upsertSlot(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Science fair setup");
    }

    @Test
    void gradeAndFeedbackFromTeacherAreVisibleOnStudentsSubmission() {
        Teacher teacher = createTeacher("T2699205", "teacher205", "8A", true);
        Student student = createStudent("S2699205", "student205", "8A");

        AssignmentForm form = new AssignmentForm();
        form.setClassName("8A");
        form.setSubject("Geography");
        form.setTitle("Map Quiz");
        form.setDueDate(LocalDate.now().plusDays(1).toString());
        Assignment assignment = assignmentService.create(form, teacher);

        AssignmentSubmission submission = submissionService.submit(assignment, student, "my answer");
        assignmentService.grade(assignment.getId(), submission.getId(), "18/20", "Well done!", teacher);

        AssignmentSubmission graded = submissionService.submissionFor(assignment.getId(), student.getStudentId()).orElseThrow();
        assertThat(graded.getGrade()).isEqualTo("18/20");
        assertThat(graded.getFeedback()).isEqualTo("Well done!");
        assertThat(graded.getGradedBy()).isEqualTo(teacher.getTeacherId());
    }
}
