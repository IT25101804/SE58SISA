package sisa.controller;

import sisa.entity.*;
import sisa.repository.*;
import sisa.entity.*;
import sisa.repository.*;
import sisa.service.MarksEntryService;
import sisa.service.ResultsAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeacherAcademicControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private TimetableSlotRepository timetableSlotRepository;
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private MarkRepository markRepository;
    @Autowired
    private MarksEntryService marksEntryService;
    @Autowired
    private ResultsAggregationService resultsAggregationService;

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

    private Exam createExam(String className, String subject, Teacher createdBy, double maxMarks) {
        Exam exam = new Exam();
        exam.setClassName(className);
        exam.setSubject(subject);
        exam.setExamName(subject + " Test");
        exam.setExamDate(LocalDate.now());
        exam.setMaxMarks(maxMarks);
        exam.setCreatedBy(createdBy);
        return examRepository.save(exam);
    }

    private Mark createMark(Exam exam, Student student, double marksObtained) {
        Mark mark = new Mark();
        mark.setExam(exam);
        mark.setStudent(student);
        mark.setMarksObtained(marksObtained);
        mark.setGrade(MarksEntryService.gradeFor(marksObtained, exam.getMaxMarks()));
        return markRepository.save(mark);
    }

    @Test
    void subjectTeacherCannotEnterMarksForAClassSubjectTheyArentAssignedTo() throws Exception {
        Student student = createStudent("S2699301", "student301", "6A");
        Teacher subjectTeacher = createTeacher("T2699301", "subjectteacher301", null, false);
        Teacher classTeacher = createTeacher("T2699302", "classteacher302", "6A", true);
        Exam exam = createExam("6A", "History", classTeacher, 100);

        mockMvc.perform(post("/teacher/academic/" + exam.getId() + "/marks")
                        .with(user("subjectteacher301").roles("TEACHER"))
                        .with(csrf())
                        .param("entries[0].studentId", student.getStudentId())
                        .param("entries[0].marksObtained", "50"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignedSubjectTeacherCanEnterMarks() throws Exception {
        Student student = createStudent("S2699303", "student303", "6B");
        Teacher subjectTeacher = createTeacher("T2699303", "subjectteacher303", null, false);

        TimetableSlot slot = new TimetableSlot();
        slot.setClassName("6B");
        slot.setSubject("Science");
        slot.setTeacher(subjectTeacher);
        slot.setDayOfWeek(java.time.DayOfWeek.MONDAY);
        slot.setPeriodNumber(1);
        timetableSlotRepository.save(slot);

        Exam exam = createExam("6B", "Science", subjectTeacher, 100);

        mockMvc.perform(post("/teacher/academic/" + exam.getId() + "/marks")
                        .with(user("subjectteacher303").roles("TEACHER"))
                        .with(csrf())
                        .param("entries[0].studentId", student.getStudentId())
                        .param("entries[0].marksObtained", "80"))
                .andExpect(status().is3xxRedirection());

        Mark saved = markRepository.findByExam_IdAndStudent_StudentId(exam.getId(), student.getStudentId()).orElseThrow();
        assertThat(saved.getGrade()).isEqualTo(Grade.A);
    }

    @Test
    void gpaCalculationFromAKnownSetOfGradesMatchesExpectedValue() {
        Student student = createStudent("S2699304", "student304", "7A");
        Teacher teacher = createTeacher("T2699304", "classteacher304", "7A", true);

        createMark(createExam("7A", "Maths", teacher, 100), student, 90);
        createMark(createExam("7A", "Science", teacher, 100), student, 65);
        createMark(createExam("7A", "English", teacher, 100), student, 55);

        Double gpa = marksEntryService.gpaFor(student.getStudentId());
        assertThat(gpa).isEqualTo(3.0);
    }

    @Test
    void studentsNeedingExtraHelpFlagsAStudentFailingTwoOrMoreSubjects() {
        Student failingStudent = createStudent("S2699305", "student305", "8A");
        Student okStudent = createStudent("S2699306", "student306", "8A");
        Teacher teacher = createTeacher("T2699305", "classteacher305", "8A", true);

        Exam mathsExam = createExam("8A", "Maths", teacher, 100);
        Exam scienceExam = createExam("8A", "Science", teacher, 100);
        Exam englishExam = createExam("8A", "English", teacher, 100);

        createMark(mathsExam, failingStudent, 20);
        createMark(scienceExam, failingStudent, 25);
        createMark(englishExam, failingStudent, 90);

        createMark(mathsExam, okStudent, 20);
        createMark(scienceExam, okStudent, 80);
        createMark(englishExam, okStudent, 85);

        List<ResultsAggregationService.ExtraHelpEntry> flagged = resultsAggregationService.studentsNeedingExtraHelp();

        assertThat(flagged).extracting(e -> e.student().getStudentId()).contains("S2699305");
        assertThat(flagged).extracting(e -> e.student().getStudentId()).doesNotContain("S2699306");
        ResultsAggregationService.ExtraHelpEntry entry = flagged.stream()
                .filter(e -> e.student().getStudentId().equals("S2699305")).findFirst().orElseThrow();
        assertThat(entry.failingSubjects()).containsExactlyInAnyOrder("Maths", "Science");
    }
}
