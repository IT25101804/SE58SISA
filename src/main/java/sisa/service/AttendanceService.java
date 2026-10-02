package sisa.service;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.AttendanceCorrectionRequestRepository;
import sisa.repository.AttendanceRecordRepository;
import sisa.repository.StudentRepository;
import sisa.repository.TeacherRepository;
import sisa.service.dto.AttendanceMarkForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private static final double OFTEN_ABSENT_THRESHOLD = 80.0;

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceCorrectionRequestRepository correctionRequestRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AttendanceEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public AttendanceService(AttendanceRecordRepository attendanceRecordRepository,
                             AttendanceCorrectionRequestRepository correctionRequestRepository,
                             StudentRepository studentRepository,
                             TeacherRepository teacherRepository,
                             AttendanceEventPublisher eventPublisher,
                             AuditLogService auditLogService) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.correctionRequestRepository = correctionRequestRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    public Teacher requireTeacher(User user) {
        return teacherRepository.findById(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("No teacher record for " + user.getUserId()));
    }

    public List<Student> rosterFor(String className) {
        return studentRepository.findByClassNameAndStatusOrderByUser_FullNameAsc(className, StudentStatus.ACTIVE);
    }

    public List<String> distinctClassNames() {
        return studentRepository.distinctClassNames();
    }

    public Map<String, AttendanceRecord> todaysRecordsByStudent(String className) {
        return recordsByStudentFor(className, LocalDate.now());
    }

    public Map<String, AttendanceRecord> recordsByStudentFor(String className, LocalDate date) {
        return attendanceRecordRepository.findByClassNameAndAttendanceDate(className, date).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getStudentId(), r -> r));
    }

    public boolean isMarkedToday(String className) {
        return !attendanceRecordRepository.findByClassNameAndAttendanceDate(className, LocalDate.now()).isEmpty();
    }

    public long presentTodayCount(String className) {
        return attendanceRecordRepository.findByClassNameAndAttendanceDate(className, LocalDate.now()).stream()
                .filter(r -> r.getStatus() == AttendanceStatus.PRESENT)
                .count();
    }

    public Double todaysSchoolWidePercentage() {
        List<AttendanceRecord> today = attendanceRecordRepository.findByAttendanceDate(LocalDate.now());
        if (today.isEmpty()) return null;
        long attended = today.stream().filter(r -> r.getStatus() != AttendanceStatus.ABSENT).count();
        return attended * 100.0 / today.size();
    }

    @Transactional
    public void markOrUpdateToday(String className, AttendanceMarkForm form, User actingUser) {
        markOrUpdate(className, LocalDate.now(), form, actingUser);
    }

    @Transactional
    public void markOrUpdate(String className, LocalDate date, AttendanceMarkForm form, User actingUser) {
        requireClassTeacherFor(className, actingUser);
        if (date == null) {
            throw new IllegalArgumentException("A date is required.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Can't mark attendance for a future date.");
        }

        List<AttendanceRecord> absentOrLateTouched = new ArrayList<>();

        for (AttendanceMarkForm.Entry entry : form.getEntries()) {
            if (entry.getStudentId() == null || entry.getStudentId().isBlank()) continue;
            Student student = studentRepository.findById(entry.getStudentId())
                    .orElseThrow(() -> new IllegalArgumentException("No such student: " + entry.getStudentId()));

            Optional<AttendanceRecord> existing = attendanceRecordRepository.findByStudent_StudentIdAndAttendanceDate(student.getStudentId(), date);
            String rawStatus = entry.getStatus();

            if (rawStatus == null || rawStatus.isBlank()) {
                if (existing.isPresent()) {
                    AttendanceRecord record = existing.get();
                    AttendanceStatus old = record.getStatus();
                    attendanceRecordRepository.delete(record);
                    auditLogService.log(student.getStudentId(), actingUser.getUserId(), "DELETE_ATTENDANCE",
                            actingUser.getFullName() + " cleared " + date + " attendance for " + student.getStudentId()
                                    + " (was " + old + ")");
                }
                continue;
            }

            AttendanceStatus newStatus = AttendanceStatus.valueOf(rawStatus);

            if (existing.isPresent()) {
                AttendanceRecord record = existing.get();
                if (record.getStatus() != newStatus) {
                    AttendanceStatus old = record.getStatus();
                    record.setStatus(newStatus);
                    record.setLastEditedBy(actingUser.getUserId());
                    record.setLastEditedAt(LocalDateTime.now());
                    attendanceRecordRepository.save(record);
                    auditLogService.log(student.getStudentId(), actingUser.getUserId(), "EDIT_ATTENDANCE",
                            actingUser.getFullName() + " changed " + date + " attendance for " + student.getStudentId()
                                    + " from " + old + " to " + newStatus);
                    if (newStatus != AttendanceStatus.PRESENT) absentOrLateTouched.add(record);
                }
            } else {
                AttendanceRecord record = new AttendanceRecord();
                record.setStudent(student);
                record.setClassName(className);
                record.setAttendanceDate(date);
                record.setStatus(newStatus);
                record.setMarkedBy(actingUser.getUserId());
                record.setMarkedAt(LocalDateTime.now());
                attendanceRecordRepository.save(record);
                if (newStatus != AttendanceStatus.PRESENT) absentOrLateTouched.add(record);
            }
        }

        eventPublisher.publishAttendanceMarked(absentOrLateTouched, actingUser);
    }

    private Teacher requireClassTeacherFor(String className, User user) {
        if (user.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Class Teacher can mark attendance.");
        }
        Teacher teacher = requireTeacher(user);
        if (!teacher.isClassTeacher() || !className.equals(teacher.getAssignedClassName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Class Teacher for " + className + " can mark or edit its attendance.");
        }
        return teacher;
    }

    public List<AttendanceRecord> historyFor(String studentId) {
        return attendanceRecordRepository.findByStudent_StudentIdOrderByAttendanceDateDesc(studentId);
    }

    public record AttendanceSummary(long totalDays, long presentDays, long lateDays, long absentDays, Double percentage) {}

    public AttendanceSummary summaryFor(String studentId) {
        List<AttendanceRecord> records = historyFor(studentId);
        long total = records.size();
        long present = records.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
        long late = records.stream().filter(r -> r.getStatus() == AttendanceStatus.LATE).count();
        long absent = records.stream().filter(r -> r.getStatus() == AttendanceStatus.ABSENT).count();
        Double percentage = total == 0 ? null : (present + late) * 100.0 / total;
        return new AttendanceSummary(total, present, late, absent, percentage);
    }

    public record ChildAttendance(Student student, List<AttendanceRecord> records, AttendanceSummary summary) {}

    public record OftenAbsentEntry(Student student, AttendanceSummary summary) {}

    public List<OftenAbsentEntry> oftenAbsent(String classNameOrNull) {
        List<Student> pool = classNameOrNull != null
                ? studentRepository.findByClassNameAndStatusOrderByUser_FullNameAsc(classNameOrNull, StudentStatus.ACTIVE)
                : studentRepository.search(null, StudentStatus.ACTIVE);

        List<OftenAbsentEntry> result = new ArrayList<>();
        for (Student student : pool) {
            AttendanceSummary summary = summaryFor(student.getStudentId());
            if (summary.percentage() != null && summary.percentage() < OFTEN_ABSENT_THRESHOLD) {
                result.add(new OftenAbsentEntry(student, summary));
            }
        }
        result.sort(Comparator.comparing(e -> e.summary().percentage()));
        return result;
    }

    @Transactional
    public void requestCorrection(String studentId, Long attendanceRecordId, AttendanceStatus requestedStatus, String reason) {
        AttendanceRecord record = attendanceRecordRepository.findById(attendanceRecordId)
                .orElseThrow(() -> new IllegalArgumentException("No such attendance record."));
        if (!record.getStudent().getStudentId().equals(studentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That attendance record isn't yours to dispute.");
        }
        AttendanceCorrectionRequest request = new AttendanceCorrectionRequest();
        request.setAttendanceRecord(record);
        request.setRequestedByStudentId(studentId);
        request.setRequestedStatus(requestedStatus);
        request.setReason(reason);
        correctionRequestRepository.save(request);
    }

    public List<AttendanceCorrectionRequest> pendingCorrectionsFor(String classNameOrNull) {
        return classNameOrNull == null
                ? correctionRequestRepository.findByStatusOrderByRequestedAtDesc(CorrectionRequestStatus.PENDING)
                : correctionRequestRepository.findByStatusAndAttendanceRecord_ClassNameOrderByRequestedAtDesc(
                        CorrectionRequestStatus.PENDING, classNameOrNull);
    }

    public List<AttendanceCorrectionRequest> requestsByStudent(String studentId) {
        return correctionRequestRepository.findByRequestedByStudentIdOrderByRequestedAtDesc(studentId);
    }

    @Transactional
    public void reviewCorrection(Long requestId, boolean approve, String note, User reviewer) {
        AttendanceCorrectionRequest request = correctionRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("No such correction request."));
        AttendanceRecord record = request.getAttendanceRecord();
        requireClassTeacherFor(record.getClassName(), reviewer);

        request.setStatus(approve ? CorrectionRequestStatus.APPROVED : CorrectionRequestStatus.REJECTED);
        request.setReviewedBy(reviewer.getUserId());
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(note);
        correctionRequestRepository.save(request);

        if (approve) {
            AttendanceStatus old = record.getStatus();
            record.setStatus(request.getRequestedStatus());
            record.setLastEditedBy(reviewer.getUserId());
            record.setLastEditedAt(LocalDateTime.now());
            attendanceRecordRepository.save(record);
            auditLogService.log(record.getStudent().getStudentId(), reviewer.getUserId(), "EDIT_ATTENDANCE",
                    reviewer.getFullName() + " approved a correction for " + record.getAttendanceDate()
                            + ": " + old + " -> " + request.getRequestedStatus());
        }
    }
}
