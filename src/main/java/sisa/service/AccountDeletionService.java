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

/**
 * The hard-delete half of User & Access Management and Student Information Management.
 *
 * Deleting is only allowed for records that carry no school history — an account that
 * was rejected/disabled before it was ever used, or a student registered by mistake.
 * Anything with attendance, marks, submissions, loans, behaviour notes, exams,
 * assignments or timetable slots attached is refused with a clear message, so the
 * existing "disable" (accounts) and "archive" (students) soft-deletes stay the path for
 * real records. The permanent Principal account can never be deleted.
 */
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

    /**
     * Principal: permanently delete a REJECTED or DISABLED account (User & Access
     * Management). An APPROVED account must be disabled first, and a PENDING one
     * approved or rejected first, so a live login is never removed by one click.
     */
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
                if (examRepository.existsByCreatedBy_TeacherId(userId)
                        || assignmentRepository.existsByTeacher_TeacherId(userId)
                        || timetableSlotRepository.existsByTeacher_TeacherId(userId)) {
                    throw new IllegalStateException("Teacher " + userId + " has exams, assignments or timetable slots "
                            + "on record, so the account can only stay disabled.");
                }
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

    /**
     * Registrar/Principal: permanently delete a student registered by mistake (Student
     * Information Management). Only allowed while the student has no school records;
     * otherwise Archive is the correct action. The linked guardian account is kept, since
     * it may be shared with siblings.
     */
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

    /** The user's own inbox rows, direct messages and saved reports go with the account; the audit log stays. */
    private void removeUserAndPersonalData(User user) {
        String userId = user.getUserId();
        notificationRepository.deleteByRecipientUserId(userId);
        messageRepository.deleteAll(messageRepository.findAllInvolving(userId));
        savedReportRepository.deleteByOwnerUserId(userId);
        userRepository.delete(user);
    }
}
