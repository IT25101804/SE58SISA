package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.MarkRepository;
import sisa.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResultsAggregationService {

    private static final int FAILING_SUBJECTS_THRESHOLD = 2;

    private final MarkRepository markRepository;
    private final StudentRepository studentRepository;
    private final MarksEntryService marksEntryService;
    private final AttendanceService attendanceService;

    public ResultsAggregationService(MarkRepository markRepository, StudentRepository studentRepository,
                                     MarksEntryService marksEntryService, AttendanceService attendanceService) {
        this.markRepository = markRepository;
        this.studentRepository = studentRepository;
        this.marksEntryService = marksEntryService;
        this.attendanceService = attendanceService;
    }

    public record RankedStudent(Student student, Double gpa) {}

    public List<RankedStudent> classRankings(String className) {
        return studentRepository.findByClassNameAndStatusOrderByUser_FullNameAsc(className, StudentStatus.ACTIVE).stream()
                .map(s -> new RankedStudent(s, marksEntryService.gpaFor(s.getStudentId())))
                .sorted(Comparator.comparing((RankedStudent r) -> r.gpa() == null ? -1.0 : r.gpa()).reversed())
                .toList();
    }

    public record SubjectPassRate(String subject, long totalMarks, long passingMarks, double passRatePercent) {}

    public List<SubjectPassRate> passRatePerSubject() {
        Map<String, List<Mark>> bySubject = markRepository.findAll().stream()
                .collect(Collectors.groupingBy(m -> m.getExam().getSubject()));
        return bySubject.entrySet().stream()
                .map(e -> {
                    long total = e.getValue().size();
                    long passing = e.getValue().stream().filter(m -> m.getGrade() != Grade.F).count();
                    return new SubjectPassRate(e.getKey(), total, passing, total == 0 ? 0 : passing * 100.0 / total);
                })
                .sorted(Comparator.comparing(SubjectPassRate::subject))
                .toList();
    }

    public record TeacherSubjectAverage(String subject, Teacher teacher, double averagePercent) {}

    public List<TeacherSubjectAverage> bestTeacherPerSubject() {
        Map<String, Map<Teacher, List<Mark>>> bySubjectThenTeacher = markRepository.findAll().stream()
                .collect(Collectors.groupingBy(m -> m.getExam().getSubject(),
                        Collectors.groupingBy(m -> m.getExam().getCreatedBy())));

        List<TeacherSubjectAverage> best = new ArrayList<>();
        for (var subjectEntry : bySubjectThenTeacher.entrySet()) {
            Teacher bestTeacher = null;
            double bestAverage = -1;
            for (var teacherEntry : subjectEntry.getValue().entrySet()) {
                double average = teacherEntry.getValue().stream()
                        .mapToDouble(m -> m.getExam().getMaxMarks() <= 0 ? 0 : m.getMarksObtained() / m.getExam().getMaxMarks() * 100.0)
                        .average().orElse(0);
                if (average > bestAverage) {
                    bestAverage = average;
                    bestTeacher = teacherEntry.getKey();
                }
            }
            best.add(new TeacherSubjectAverage(subjectEntry.getKey(), bestTeacher, bestAverage));
        }
        return best.stream().sorted(Comparator.comparing(TeacherSubjectAverage::subject)).toList();
    }

    public record ExtraHelpEntry(Student student, Set<String> failingSubjects, Double attendancePercentage) {}

    public List<ExtraHelpEntry> studentsNeedingExtraHelp() {
        List<Student> allActive = studentRepository.search(null, StudentStatus.ACTIVE);
        Map<String, List<Mark>> marksByStudent = markRepository.findAll().stream()
                .collect(Collectors.groupingBy(m -> m.getStudent().getStudentId()));

        List<ExtraHelpEntry> result = new ArrayList<>();
        for (Student student : allActive) {
            List<Mark> marks = marksByStudent.getOrDefault(student.getStudentId(), List.of());
            Set<String> failingSubjects = marks.stream()
                    .filter(m -> m.getGrade() == Grade.F)
                    .map(m -> m.getExam().getSubject())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (failingSubjects.size() >= FAILING_SUBJECTS_THRESHOLD) {
                Double attendancePercentage = attendanceService.summaryFor(student.getStudentId()).percentage();
                result.add(new ExtraHelpEntry(student, failingSubjects, attendancePercentage));
            }
        }
        result.sort(Comparator.comparing((ExtraHelpEntry e) -> e.failingSubjects().size()).reversed());
        return result;
    }
}
