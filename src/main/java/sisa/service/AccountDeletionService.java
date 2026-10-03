package sisa.service;

import sisa.entity.*;
import sisa.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sisa.entity.AccountStatus;
import sisa.entity.Role;
import sisa.entity.Student;
import sisa.entity.User;
import sisa.repository.*;

@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ParentRepository parentRepository;
    private final RegistrarRepository registrarRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MarkRepository markRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final LibraryLoanRepository libraryLoanRepository;
    private final BehaviourNoteRepository behaviourNoteRepository;
    private final ExamRepository examRepository;
    private final AssignmentRepository assignmentRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final NotificationRepository notificationRepository;
    private final MessageRepository messageRepository;
    private final SavedReportRepository savedReportRepository;
    private final AuditLogService auditLogService;

    public AccountDeletionService(UserRepository userRepository, StudentRepository studentRepository,
                                  TeacherRepository teacherRepository, ParentRepository parentRepository,
                                  RegistrarRepository registrarRepository,
                                  AttendanceRecordRepository attendanceRecordRepository, MarkRepository markRepository,
                                  AssignmentSubmissionRepository assignmentSubmissionRepository,
                                  LibraryLoanRepository libraryLoanRepository,
                                  BehaviourNoteRepository behaviourNoteRepository, ExamRepository examRepository,
                                  AssignmentRepository assignmentRepository,
                                  TimetableSlotRepository timetableSlotRepository,
                                  NotificationRepository notificationRepository, MessageRepository messageRepository,
                                  SavedReportRepository savedReportRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.parentRepository = parentRepository;
        this.registrarRepository = registrarRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.markRepository = markRepository;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
        this.libraryLoanRepository = libraryLoanRepository;
        this.behaviourNoteRepository = behaviourNoteRepository;
        this.examRepository = examRepository;
        this.assignmentRepository = assignmentRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.notificationRepository = notificationRepository;
        this.messageRepository = messageRepository;
        this.savedReportRepository = savedReportRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void deleteAccount(String userId, User actingUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No such account: " + userId));
        if (user.getRole() == Role.PRINCIPAL || !user.isDeletable()) {
            throw new IllegalStateException("The permanent Principal account cannot be deleted.");
        }
        if (user.getUserId().equals(actingUser.getUserId())) {
            throw new IllegalStateException("You cannot delete your own account.");
        }
        if (user.getStatus() != AccountStatus.REJECTED && user.getStatus() != AccountStatus.DISABLED) {
            throw new IllegalStateException("Only rejected or disabled accounts can be deleted — "
                    + (user.getStatus() == AccountStatus.PENDING ? "approve or reject it first." : "disable it first."));
        }

        switch (user.getRole()) {
            case STUDENT -> {
                assertStudentHasNoRecords(userId);
                studentRepository.findById(userId).ifPresent(studentRepository::delete);
            }
            case TEACHER -> {
                assertTeacherHasNoRecords(userId, "so the account can only stay disabled.");
                teacherRepository.findById(userId).ifPresent(teacherRepository::delete);
            }
            case PARENT -> {
                if (studentRepository.existsByParent_UserId(userId)) {
                    throw new IllegalStateException("Parent " + userId + " is still linked to a student as guardian, "
                            + "so the account can only stay disabled.");
                }
                parentRepository.findById(userId).ifPresent(parentRepository::delete);
            }
            case REGISTRAR -> registrarRepository.findById(userId).ifPresent(registrarRepository::delete);
            default -> { }
        }

        removeUserAndPersonalData(user);
        auditLogService.log(userId, actingUser.getUserId(), "DELETE_ACCOUNT",
                actingUser.getFullName() + " permanently deleted " + user.getRole() + " account " + userId
                        + " (" + user.getFullName() + ")");
    }

    @Transactional
    public void deleteStudent(String studentId, User actingUser) {
        if (actingUser.getRole() != Role.REGISTRAR && actingUser.getRole() != Role.PRINCIPAL) {
            throw new IllegalStateException("Only the Registrar or Principal can manage student records.");
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId));
        assertStudentHasNoRecords(studentId);

        User user = student.getUser();
        studentRepository.delete(student);
        removeUserAndPersonalData(user);
        auditLogService.log(studentId, actingUser.getUserId(), "DELETE_STUDENT",
                actingUser.getFullName() + " permanently deleted student " + studentId
                        + " (" + user.getFullName() + ") — registered in error");
    }

    @Transactional
    public void deleteTeacher(String teacherId, User actingUser) {
        if (actingUser.getRole() != Role.REGISTRAR && actingUser.getRole() != Role.PRINCIPAL) {
            throw new IllegalStateException("Only the Registrar or Principal can delete teacher accounts.");
        }
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("No such teacher: " + teacherId));
        assertTeacherHasNoRecords(teacherId,
                "so it can't be deleted. Remove their timetable periods, exams and assignments first, or ask the Principal to disable the account.");

        User user = teacher.getUser();
        teacherRepository.delete(teacher);
        removeUserAndPersonalData(user);
        auditLogService.log(teacherId, actingUser.getUserId(), "DELETE_ACCOUNT",
                actingUser.getFullName() + " permanently deleted TEACHER account " + teacherId
                        + " (" + user.getFullName() + ")");
    }

    private void assertTeacherHasNoRecords(String teacherId, String consequence) {
        if (examRepository.existsByCreatedBy_TeacherId(teacherId)
                || assignmentRepository.existsByTeacher_TeacherId(teacherId)
                || timetableSlotRepository.existsByTeacher_TeacherId(teacherId)) {
            throw new IllegalStateException("Teacher " + teacherId + " has exams, assignments or timetable slots "
                    + "on record, " + consequence);
        }
    }

    private void assertStudentHasNoRecords(String studentId) {
        if (attendanceRecordRepository.existsByStudent_StudentId(studentId)
                || markRepository.existsByStudent_StudentId(studentId)
                || assignmentSubmissionRepository.existsByStudent_StudentId(studentId)
                || libraryLoanRepository.existsByStudent_StudentId(studentId)
                || behaviourNoteRepository.existsByStudent_StudentId(studentId)) {
            throw new IllegalStateException("Student " + studentId + " already has attendance, marks, submissions, "
                    + "library loans or behaviour notes on record — archive the student instead.");
        }
    }

    private void removeUserAndPersonalData(User user) {
        String userId = user.getUserId();
        notificationRepository.deleteByRecipientUserId(userId);
        messageRepository.deleteAll(messageRepository.findAllInvolving(userId));
        savedReportRepository.deleteByOwnerUserId(userId);
        userRepository.delete(user);
    }
}
