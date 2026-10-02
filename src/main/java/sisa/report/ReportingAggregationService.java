package sisa.report;

import sisa.entity.*;
import sisa.repository.*;
import org.springframework.stereotype.Service;
import sisa.entity.*;
import sisa.repository.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportingAggregationService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MarkRepository markRepository;
    private final ExamRepository examRepository;
    private final ResourceBookingRepository resourceBookingRepository;

    public ReportingAggregationService(StudentRepository studentRepository, UserRepository userRepository,
                                       TeacherRepository teacherRepository,
                                       Optional<AttendanceRecordRepository> attendanceRecordRepository,
                                       Optional<MarkRepository> markRepository,
                                       Optional<ExamRepository> examRepository,
                                       Optional<ResourceBookingRepository> resourceBookingRepository) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.attendanceRecordRepository = attendanceRecordRepository.orElse(null);
        this.markRepository = markRepository.orElse(null);
        this.examRepository = examRepository.orElse(null);
        this.resourceBookingRepository = resourceBookingRepository.orElse(null);
    }

    public long totalEnrolledStudents() {
        return studentRepository.countByStatus(StudentStatus.ACTIVE);
    }

    public long pendingApprovalsCount() {
        return userRepository.findByStatus(AccountStatus.PENDING).size();
    }

    public Double todaysAttendancePercentageOrNull() {
        if (attendanceRecordRepository == null) return null;
        List<AttendanceRecord> today = attendanceRecordRepository.findByAttendanceDate(LocalDate.now());
        if (today.isEmpty()) return null;
        long attended = today.stream().filter(r -> r.getStatus() != AttendanceStatus.ABSENT).count();
        return attended * 100.0 / today.size();
    }

    public Long roomsBookedTodayOrNull() {
        if (resourceBookingRepository == null) return null;
        return resourceBookingRepository.countByBookingDateAndStatusAndResource_Type(
                LocalDate.now(), BookingStatus.APPROVED, ResourceType.ROOM);
    }

    public record ChartSeries(List<String> labels, List<Double> values, String seriesLabel, String chartType) {}
    public record Report(ReportData data, ChartSeries chart) {}

    public Report build(ReportType type, LocalDate from, LocalDate to, String className, boolean includeStaffPerformance) {
        return switch (type) {
            case ENROLMENT -> enrolmentReport(from, to);
            case ATTENDANCE -> attendanceReport(from, to);
            case ACADEMIC -> academicReport(from, to, className);
            case STAFF -> staffReport(includeStaffPerformance);
            case STAFF_PAY -> staffPayReport();
            case TRANSFER -> transferReport();
            case CLASS_LIST -> classListReport(className);
        };
    }

    private boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) return false;
        if (from != null && date.isBefore(from)) return false;
        return to == null || !date.isAfter(to);
    }

    private Report enrolmentReport(LocalDate from, LocalDate to) {
        List<Student> students = studentRepository.findAll().stream()
                .filter(s -> from == null && to == null || inRange(s.getEnrollmentDate(), from, to))
                .toList();

        Map<String, Long> byMonth = students.stream()
                .filter(s -> s.getEnrollmentDate() != null)
                .collect(Collectors.groupingBy(s -> s.getEnrollmentDate().withDayOfMonth(1).format(MONTH_LABEL),
                        LinkedHashMap::new, Collectors.counting()));

        List<String> columns = List.of("Month", "New Enrolments");
        List<List<String>> rows = byMonth.entrySet().stream()
                .map(e -> List.of(e.getKey(), String.valueOf(e.getValue())))
                .toList();

        ChartSeries chart = new ChartSeries(new ArrayList<>(byMonth.keySet()),
                byMonth.values().stream().map(Long::doubleValue).toList(), "New Enrolments", "line");
        return new Report(new ReportData("Enrolment Report", columns, rows), chart);
    }

    private Report attendanceReport(LocalDate from, LocalDate to) {
        List<String> columns = List.of("Date", "Present/Late", "Absent", "Attendance %");
        if (attendanceRecordRepository == null) {
            return new Report(new ReportData("Attendance Trend (module not available)", columns, List.of()), null);
        }

        Map<LocalDate, List<AttendanceRecord>> byDate = attendanceRecordRepository.findAll().stream()
                .filter(r -> from == null && to == null || inRange(r.getAttendanceDate(), from, to))
                .collect(Collectors.groupingBy(AttendanceRecord::getAttendanceDate, TreeMap::new, Collectors.toList()));

        List<List<String>> rows = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (var entry : byDate.entrySet()) {
            long total = entry.getValue().size();
            long attended = entry.getValue().stream().filter(r -> r.getStatus() != AttendanceStatus.ABSENT).count();
            long absent = total - attended;
            double pct = total == 0 ? 0 : attended * 100.0 / total;
            rows.add(List.of(entry.getKey().toString(), String.valueOf(attended), String.valueOf(absent),
                    String.format("%.1f", pct)));
            labels.add(entry.getKey().toString());
            values.add(pct);
        }
        ChartSeries chart = new ChartSeries(labels, values, "Attendance %", "line");
        return new Report(new ReportData("Attendance Trend", columns, rows), chart);
    }

    private Report academicReport(LocalDate from, LocalDate to, String className) {
        List<String> columns = List.of("Subject", "Total Marks", "Passing", "Pass Rate %");
        String title = (className == null || className.isBlank())
                ? "Academic Pass Rate by Subject"
                : "Academic Pass Rate by Subject — " + className;

        if (markRepository == null || examRepository == null) {
            return new Report(new ReportData(title + " (module not available)", columns, List.of()), null);
        }

        List<Mark> marks = markRepository.findAll().stream()
                .filter(m -> from == null && to == null || inRange(m.getExam().getExamDate(), from, to))
                .filter(m -> className == null || className.isBlank() || className.equals(m.getExam().getClassName()))
                .toList();

        Map<String, List<Mark>> bySubject = marks.stream()
                .collect(Collectors.groupingBy(m -> m.getExam().getSubject(), TreeMap::new, Collectors.toList()));

        List<List<String>> rows = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (var entry : bySubject.entrySet()) {
            long total = entry.getValue().size();
            long passing = entry.getValue().stream().filter(m -> m.getGrade() != Grade.F).count();
            double pct = total == 0 ? 0 : passing * 100.0 / total;
            rows.add(List.of(entry.getKey(), String.valueOf(total), String.valueOf(passing), String.format("%.1f", pct)));
            labels.add(entry.getKey());
            values.add(pct);
        }
        ChartSeries chart = new ChartSeries(labels, values, "Pass Rate %", "bar");
        return new Report(new ReportData(title, columns, rows), chart);
    }

    private Report staffReport(boolean includeStaffPerformance) {
        List<Teacher> teachers = teacherRepository.findAll();

        List<String> columns = includeStaffPerformance
                ? List.of("Teacher ID", "Name", "Subject Specialty", "Class Teacher Of", "Exams Created", "Marks Entered", "Avg Student %")
                : List.of("Teacher ID", "Name", "Subject Specialty", "Class Teacher Of");

        List<List<String>> rows = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (Teacher teacher : teachers) {
            List<String> row = new ArrayList<>(List.of(
                    teacher.getTeacherId(), teacher.getUser().getFullName(),
                    Objects.toString(teacher.getSubjectSpecialty(), "—"),
                    teacher.isClassTeacher() && teacher.getAssignedClassName() != null ? teacher.getAssignedClassName() : "—"));

            if (includeStaffPerformance) {
                long examsCreated = examRepository == null ? 0
                        : examRepository.findByCreatedBy_TeacherIdOrderByExamDateDesc(teacher.getTeacherId()).size();
                List<Mark> teacherMarks = markRepository == null ? List.of()
                        : markRepository.findAll().stream()
                                .filter(m -> teacher.getTeacherId().equals(m.getEnteredBy())).toList();
                OptionalDouble avgPct = teacherMarks.stream()
                        .mapToDouble(m -> m.getExam().getMaxMarks() <= 0 ? 0 : m.getMarksObtained() / m.getExam().getMaxMarks() * 100.0)
                        .average();

                row.add(String.valueOf(examsCreated));
                row.add(String.valueOf(teacherMarks.size()));
                row.add(avgPct.isPresent() ? String.format("%.1f", avgPct.getAsDouble()) : "—");

                if (avgPct.isPresent()) {
                    labels.add(teacher.getUser().getFullName());
                    values.add(avgPct.getAsDouble());
                }
            }
            rows.add(row);
        }

        ChartSeries chart = includeStaffPerformance && !labels.isEmpty()
                ? new ChartSeries(labels, values, "Avg Student %", "bar")
                : null;
        return new Report(new ReportData("Staff Directory", columns, rows), chart);
    }

    private Report staffPayReport() {
        List<Teacher> teachers = teacherRepository.findAll();
        List<String> columns = List.of("Teacher ID", "Name", "Subject Specialty", "Monthly Salary");

        List<List<String>> rows = new ArrayList<>();
        double total = 0;
        for (Teacher teacher : teachers) {
            Double salary = teacher.getMonthlySalary();
            rows.add(List.of(teacher.getTeacherId(), teacher.getUser().getFullName(),
                    Objects.toString(teacher.getSubjectSpecialty(), "—"),
                    salary != null ? String.format("%.2f", salary) : "—"));
            if (salary != null) total += salary;
        }
        rows.add(List.of("", "", "Total monthly payroll", String.format("%.2f", total)));
        return new Report(new ReportData("Staff Pay Report", columns, rows), null);
    }

    private Report transferReport() {
        List<String> columns = List.of("Student ID", "Name", "Class", "Status", "Note");
        List<List<String>> rows = studentRepository.search(null, StudentStatus.TRANSFERRED).stream()
                .map(s -> List.of(s.getStudentId(), s.getUser().getFullName(), Objects.toString(s.getClassName(), "—"),
                        s.getStatus().name(), Objects.toString(s.getStatusNote(), "")))
                .toList();
        return new Report(new ReportData("Transferred Students", columns, rows), null);
    }

    private Report classListReport(String className) {
        List<String> columns = List.of("Student ID", "Name", "Guardian", "Guardian Contact");
        if (className == null || className.isBlank()) {
            return new Report(new ReportData("Class List (choose a class)", columns, List.of()), null);
        }
        List<List<String>> rows = studentRepository.findByClassNameAndStatusOrderByUser_FullNameAsc(className, StudentStatus.ACTIVE).stream()
                .map(s -> List.of(s.getStudentId(), s.getUser().getFullName(),
                        Objects.toString(s.getGuardianName(), "—"), Objects.toString(s.getGuardianContact(), "—")))
                .toList();
        return new Report(new ReportData("Class List — " + className, columns, rows), null);
    }
}
