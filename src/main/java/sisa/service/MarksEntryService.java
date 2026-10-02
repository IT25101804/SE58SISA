package sisa.service;

import sisa.entity.*;
import sisa.repository.*;
import sisa.entity.*;
import sisa.repository.*;
import sisa.service.dto.ExamForm;
import sisa.service.dto.MarksEntryForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Academic Management: exams, marks entry, grading and report cards (report FR-07,
 * FR-08, business rule 1). A Class Teacher may act on any subject in their own class;
 * a Subject Teacher only on the exact class+subject they're assigned to via a
 * TimetableSlot (Module 4) — the same restriction pattern as Module 3's attendance
 * rule, and it surfaces the same way: a 403 for anyone else.
 */
@Service
public class MarksEntryService {

    /** Percentage boundaries for the letter grade — simple and fixed, no per-subject curve. */
    private static final Map<Grade, Integer> GRADE_POINTS = Map.of(
            Grade.A, 4, Grade.B, 3, Grade.C, 2, Grade.D, 1, Grade.F, 0);

    private final ExamRepository examRepository;
    private final MarkRepository markRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final SubjectRepository subjectRepository;

    public MarksEntryService(ExamRepository examRepository, MarkRepository markRepository,
                             StudentRepository studentRepository, TeacherRepository teacherRepository,
                             TimetableSlotRepository timetableSlotRepository, SubjectRepository subjectRepository) {
        this.examRepository = examRepository;
        this.markRepository = markRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.subjectRepository = subjectRepository;
    }

    // ---------- grading math ----------

    public static Grade gradeFor(double marksObtained, double maxMarks) {
        double pct = maxMarks <= 0 ? 0 : (marksObtained / maxMarks) * 100.0;
        if (pct >= 75) return Grade.A;
        if (pct >= 60) return Grade.B;
        if (pct >= 50) return Grade.C;
        if (pct >= 40) return Grade.D;
        return Grade.F;
    }

    /** Null when the student has no marks yet, rather than a misleading 0.0. */
    public Double gpaFor(String studentId) {
        List<Mark> marks = markRepository.findByStudent_StudentIdOrderByExam_ExamDateDesc(studentId);
        if (marks.isEmpty()) return null;
        int totalPoints = marks.stream().mapToInt(m -> GRADE_POINTS.get(m.getGrade())).sum();
        return totalPoints / (double) marks.size();
    }

    // ---------- authorization ----------

    public Teacher requireTeacher(User user) {
        return teacherRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("No teacher record for " + user.getUserId()));
    }

    /** Class Teacher of this class (any subject), or the Subject Teacher assigned to this exact class+subject. */
    private Teacher requireSubjectAccess(String className, String subject, User user) {
        if (user.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a teacher can manage exams and marks.");
        }
        Teacher teacher = requireTeacher(user);
        boolean isClassTeacherHere = teacher.isClassTeacher() && className.equals(teacher.getAssignedClassName());
        boolean isAssignedSubjectTeacher = timetableSlotRepository
                .existsByTeacher_TeacherIdAndClassNameAndSubject(teacher.getTeacherId(), className, subject);
        if (!isClassTeacherHere && !isAssignedSubjectTeacher) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not assigned to teach " + subject + " for " + className + ".");
        }
        return teacher;
    }

    /** Only the Class Teacher — report cards span every subject, which a Subject Teacher must not see (rule 1). */
    private Teacher requireClassTeacherFor(String className, User user) {
        if (user.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Class Teacher can generate report cards.");
        }
        Teacher teacher = requireTeacher(user);
        if (!teacher.isClassTeacher() || !className.equals(teacher.getAssignedClassName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Class Teacher for " + className + " can generate its report cards.");
        }
        return teacher;
    }

    // ---------- exams ----------

    /**
     * Valid Class -> Subjects choices for the "New Exam" form, kept exactly in sync with
     * {@link #requireSubjectAccess}: the Class Teacher's own class (any subject, since a
     * Class Teacher may set an exam in their class for any subject), plus every class+subject
     * this teacher is actually assigned to teach via the timetable (Module 4). Driving the
     * form's dropdowns from this same source is what stops someone typing a class/subject
     * that then gets rejected as "not assigned to teach it" (a 403) after they hit submit.
     */
    public LinkedHashMap<String, List<String>> classSubjectOptionsFor(Teacher teacher) {
        LinkedHashMap<String, LinkedHashSet<String>> options = new LinkedHashMap<>();

        if (teacher.isClassTeacher() && teacher.getAssignedClassName() != null) {
            List<String> allSubjects = subjectRepository.findAllByOrderByNameAsc().stream()
                    .map(Subject::getName)
                    .toList();
            options.computeIfAbsent(teacher.getAssignedClassName(), k -> new LinkedHashSet<>()).addAll(allSubjects);
        }

        for (TimetableSlot slot : timetableSlotRepository.findByTeacher_TeacherIdOrderByPeriodNumberAsc(teacher.getTeacherId())) {
            options.computeIfAbsent(slot.getClassName(), k -> new LinkedHashSet<>()).add(slot.getSubject());
        }

        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        options.forEach((className, subjects) -> result.put(className, new ArrayList<>(subjects)));
        return result;
    }

    @Transactional
    public Exam createExam(ExamForm form, User actingUser) {
        Teacher teacher = requireSubjectAccess(form.getClassName(), form.getSubject(), actingUser);
        Exam exam = new Exam();
        exam.setClassName(form.getClassName());
        exam.setSubject(form.getSubject());
        exam.setExamName(form.getExamName());
        exam.setExamDate(LocalDate.parse(form.getExamDate()));
        exam.setMaxMarks(form.getMaxMarks());
        exam.setCreatedBy(teacher);
        return examRepository.save(exam);
    }

    public List<Exam> listForClass(String className) {
        return examRepository.findByClassNameOrderByExamDateDesc(className);
    }

    /** Every exam this teacher may act on: their own class (any subject) plus any subject they're assigned elsewhere. */
    public List<Exam> listForTeacher(Teacher teacher) {
        List<Exam> ownClass = teacher.getAssignedClassName() != null && teacher.isClassTeacher()
                ? examRepository.findByClassNameOrderByExamDateDesc(teacher.getAssignedClassName())
                : List.of();
        List<Exam> created = examRepository.findByCreatedBy_TeacherIdOrderByExamDateDesc(teacher.getTeacherId());
        LinkedHashMap<Long, Exam> byId = new LinkedHashMap<>();
        for (Exam e : created) byId.put(e.getId(), e);
        for (Exam e : ownClass) byId.putIfAbsent(e.getId(), e);
        return new ArrayList<>(byId.values());
    }

    /**
     * Same access rule as entering marks (requireSubjectAccess). Marks reference the exam via
     * a foreign key, so they're removed first, then the exam itself.
     */
    @Transactional
    public void deleteExam(Long examId, User actingUser) {
        Exam exam = getOrThrow(examId);
        requireSubjectAccess(exam.getClassName(), exam.getSubject(), actingUser);
        markRepository.deleteAll(markRepository.findByExam_Id(examId));
        examRepository.delete(exam);
    }

    public Exam getOrThrow(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("No such exam: " + examId));
    }

    // ---------- marks entry ----------

    public Map<String, Mark> marksByStudent(Long examId) {
        Map<String, Mark> result = new HashMap<>();
        for (Mark m : markRepository.findByExam_Id(examId)) result.put(m.getStudent().getStudentId(), m);
        return result;
    }

    @Transactional
    public void enterMarks(Long examId, MarksEntryForm form, User actingUser) {
        Exam exam = getOrThrow(examId);
        Teacher teacher = requireSubjectAccess(exam.getClassName(), exam.getSubject(), actingUser);

        for (MarksEntryForm.Entry entry : form.getEntries()) {
            if (entry.getStudentId() == null || entry.getStudentId().isBlank()) continue;

            Optional<Mark> existing = markRepository.findByExam_IdAndStudent_StudentId(examId, entry.getStudentId());

            // A field left blank on save means "clear this mark" (undoing a wrong entry),
            // not "leave whatever's there alone" — previously this was silently ignored, so
            // a teacher who entered a mark for the wrong student, say, had no way to un-enter
            // it. A student who was simply never graded has no existing row, so this is a
            // harmless no-op for them.
            if (entry.getMarksObtained() == null || entry.getMarksObtained().isBlank()) {
                existing.ifPresent(markRepository::delete);
                continue;
            }

            double marksObtained;
            try {
                marksObtained = Double.parseDouble(entry.getMarksObtained());
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(
                        "\"" + entry.getMarksObtained() + "\" isn't a valid number for " + entry.getFullName() + ".");
            }
            if (marksObtained < 0 || marksObtained > exam.getMaxMarks()) {
                throw new IllegalArgumentException(
                        entry.getFullName() + "'s mark must be between 0 and " + exam.getMaxMarks() + ".");
            }

            Student student = studentRepository.findById(entry.getStudentId())
                    .orElseThrow(() -> new IllegalArgumentException("No such student: " + entry.getStudentId()));

            // Same row every time for this (exam, student) pair — correcting a typo just
            // overwrites marksObtained/grade on the row already found above, it never creates
            // a second one (the DB's uk_marks_exam_student constraint backs this up too).
            Mark mark = existing.orElseGet(Mark::new);
            mark.setExam(exam);
            mark.setStudent(student);
            mark.setMarksObtained(marksObtained);
            mark.setGrade(gradeFor(marksObtained, exam.getMaxMarks()));
            mark.setEnteredBy(teacher.getTeacherId());
            mark.setEnteredAt(LocalDateTime.now());
            markRepository.save(mark);
        }
    }

    // ---------- report cards ----------

    public record ReportCardEntry(String subject, String examName, LocalDate examDate, double marksObtained, double maxMarks, Grade grade) {}
    public record ReportCard(Student student, List<ReportCardEntry> entries, Double gpa) {}

    public ReportCard reportCardFor(String studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId));
        List<ReportCardEntry> entries = markRepository.findByStudent_StudentIdOrderByExam_ExamDateDesc(studentId).stream()
                .map(m -> new ReportCardEntry(m.getExam().getSubject(), m.getExam().getExamName(), m.getExam().getExamDate(),
                        m.getMarksObtained(), m.getExam().getMaxMarks(), m.getGrade()))
                .toList();
        return new ReportCard(student, entries, gpaFor(studentId));
    }

    /** Only the Class Teacher for this class may call this (see requireClassTeacherFor). */
    public ReportCard reportCardForAsClassTeacher(String studentId, String className, User actingTeacher) {
        requireClassTeacherFor(className, actingTeacher);
        return reportCardFor(studentId);
    }

    // ---------- term-over-term trend (business rule 3) ----------

    /**
     * No Term/Semester entity exists yet (that's a natural fit for Module 7's reporting),
     * so this buckets by exam month as the closest honest approximation of "term" available
     * from the data modeled today.
     */
    public record GpaTrendPoint(String label, double gpa) {}

    public List<GpaTrendPoint> monthlyGpaTrend(String studentId) {
        DateTimeFormatter monthLabel = DateTimeFormatter.ofPattern("MMM yyyy");
        Map<String, List<Mark>> byMonth = markRepository.findByStudent_StudentIdOrderByExam_ExamDateDesc(studentId).stream()
                .collect(Collectors.groupingBy(m -> m.getExam().getExamDate().withDayOfMonth(1).format(monthLabel),
                        LinkedHashMap::new, Collectors.toList()));
        List<GpaTrendPoint> trend = new ArrayList<>();
        byMonth.forEach((label, marks) -> {
            double avg = marks.stream().mapToInt(m -> GRADE_POINTS.get(m.getGrade())).average().orElse(0);
            trend.add(new GpaTrendPoint(label, avg));
        });
        Collections.reverse(trend); // oldest month first, for a left-to-right chart
        return trend;
    }
}
