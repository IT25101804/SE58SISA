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
import java.util.*;

@Service
public class MarksEntryService {

    private static final Map<Grade, Integer> GRADE_POINTS = Map.of(
            Grade.A, 4, Grade.B, 3, Grade.C, 2, Grade.D, 1, Grade.F, 0);

    private final ExamRepository examRepository;
    private final MarkRepository markRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicTermRepository academicTermRepository;

    public MarksEntryService(ExamRepository examRepository, MarkRepository markRepository,
                             StudentRepository studentRepository, TeacherRepository teacherRepository,
                             TimetableSlotRepository timetableSlotRepository, SubjectRepository subjectRepository,
                             AcademicTermRepository academicTermRepository) {
        this.examRepository = examRepository;
        this.markRepository = markRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.subjectRepository = subjectRepository;
        this.academicTermRepository = academicTermRepository;
    }

    // ---------- terms ----------

    /** Every term, newest first — the choices for the term pickers. */
    public List<AcademicTerm> allTerms() {
        return academicTermRepository.findAllByOrderByStartDateDesc();
    }

    public AcademicTerm currentTerm() {
        return academicTermRepository.findByCurrentTrue().orElse(null);
    }

    /** The chosen term, or the current term when none (or an unknown one) is chosen. */
    public AcademicTerm termOrCurrent(Long termId) {
        if (termId != null) {
            Optional<AcademicTerm> chosen = academicTermRepository.findById(termId);
            if (chosen.isPresent()) return chosen.get();
        }
        return currentTerm();
    }

    /** An exam's term; exams created before terms existed fall into the term their date is in. */
    public AcademicTerm termOf(Exam exam, List<AcademicTerm> terms) {
        if (exam.getTerm() != null) return exam.getTerm();
        LocalDate date = exam.getExamDate();
        return terms.stream()
                .filter(t -> !date.isBefore(t.getStartDate()) && !date.isAfter(t.getEndDate()))
                .findFirst().orElse(null);
    }

    private static boolean sameTerm(AcademicTerm a, AcademicTerm b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }

    public static Grade gradeFor(double marksObtained, double maxMarks) {
        double pct = maxMarks <= 0 ? 0 : (marksObtained / maxMarks) * 100.0;
        if (pct >= 75) return Grade.A;
        if (pct >= 60) return Grade.B;
        if (pct >= 50) return Grade.C;
        if (pct >= 40) return Grade.D;
        return Grade.F;
    }

    public Double gpaFor(String studentId) {
        List<Mark> marks = markRepository.findByStudent_StudentIdOrderByExam_ExamDateDesc(studentId);
        if (marks.isEmpty()) return null;
        int totalPoints = marks.stream().mapToInt(m -> GRADE_POINTS.get(m.getGrade())).sum();
        return totalPoints / (double) marks.size();
    }

    public Teacher requireTeacher(User user) {
        return teacherRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("No teacher record for " + user.getUserId()));
    }

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
        AcademicTerm term = termOrCurrent(form.getTermId());
        exam.setTerm(term != null ? term : termOf(exam, allTerms()));
        return examRepository.save(exam);
    }

    public List<Exam> listForClass(String className) {
        return examRepository.findByClassNameOrderByExamDateDesc(className);
    }

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

    public record ReportCardEntry(String subject, String examName, LocalDate examDate, double marksObtained, double maxMarks, Grade grade) {}

    /** One term's results: the marks of that term's exams, their total, and the average mark (null when none). */
    public record ReportCard(Student student, AcademicTerm term, List<ReportCardEntry> entries,
                             double totalMarks, Double averageMark) {}

    /** The current term's report card. */
    public ReportCard reportCardFor(String studentId) {
        return reportCardFor(studentId, null);
    }

    /** The report card for the chosen term (the current term when termId is null). */
    public ReportCard reportCardFor(String studentId, Long termId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId));
        AcademicTerm term = termOrCurrent(termId);
        List<AcademicTerm> terms = allTerms();
        List<ReportCardEntry> entries = markRepository.findByStudent_StudentIdOrderByExam_ExamDateDesc(studentId).stream()
                .filter(m -> term == null || sameTerm(termOf(m.getExam(), terms), term))
                .map(m -> new ReportCardEntry(m.getExam().getSubject(), m.getExam().getExamName(), m.getExam().getExamDate(),
                        m.getMarksObtained(), m.getExam().getMaxMarks(), m.getGrade()))
                .toList();
        double total = entries.stream().mapToDouble(ReportCardEntry::marksObtained).sum();
        Double average = entries.isEmpty() ? null : total / entries.size();
        return new ReportCard(student, term, entries, total, average);
    }

    public ReportCard reportCardForAsClassTeacher(String studentId, String className, User actingTeacher, Long termId) {
        requireClassTeacherFor(className, actingTeacher);
        // The student must actually be in this Class Teacher's class.
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId));
        if (!className.equals(student.getClassName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Class Teacher for " + student.getClassName() + " can view this student's report card.");
        }
        return reportCardFor(studentId, termId);
    }

    /** The current term's average mark, or null when the student has no marks this term. */
    public Double currentTermAverage(String studentId) {
        return reportCardFor(studentId, null).averageMark();
    }

    public record TermAverage(String label, double average) {}

    /** Average mark per term, oldest term first — the term-over-term trend (business rule 3). */
    public List<TermAverage> termAverageTrend(String studentId) {
        List<AcademicTerm> terms = allTerms();
        Map<Long, AcademicTerm> termById = new HashMap<>();
        Map<Long, List<Mark>> byTerm = new HashMap<>();
        for (Mark m : markRepository.findByStudent_StudentIdOrderByExam_ExamDateDesc(studentId)) {
            AcademicTerm t = termOf(m.getExam(), terms);
            if (t == null) continue;
            termById.putIfAbsent(t.getId(), t);
            byTerm.computeIfAbsent(t.getId(), k -> new ArrayList<>()).add(m);
        }
        return byTerm.entrySet().stream()
                .sorted(Comparator.comparing(e -> termById.get(e.getKey()).getStartDate()))
                .map(e -> new TermAverage(termById.get(e.getKey()).getName(),
                        e.getValue().stream().mapToDouble(Mark::getMarksObtained).average().orElse(0)))
                .toList();
    }
}
