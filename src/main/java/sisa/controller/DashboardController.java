package sisa.controller;

import sisa.entity.AccountStatus;
import sisa.entity.Role;
import sisa.entity.Student;
import sisa.entity.StudentStatus;
import sisa.entity.Teacher;
import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.repository.NotificationRepository;
import sisa.service.AssignmentService;
import sisa.service.AttendanceService;
import sisa.service.MarksEntryService;
import sisa.service.ResultsAggregationService;
import sisa.service.SubmissionService;
import sisa.service.TimetableService;
import sisa.report.ReportingAggregationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;

@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AttendanceService attendanceService;
    private final TimetableService timetableService;
    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;
    private final MarksEntryService marksEntryService;
    private final ResultsAggregationService resultsAggregationService;
    private final NotificationRepository notificationRepository;
    private final ReportingAggregationService reportingAggregationService;

    public DashboardController(UserRepository userRepository, StudentRepository studentRepository,
                               AttendanceService attendanceService, TimetableService timetableService,
                               AssignmentService assignmentService, SubmissionService submissionService,
                               MarksEntryService marksEntryService, ResultsAggregationService resultsAggregationService,
                               NotificationRepository notificationRepository,
                               ReportingAggregationService reportingAggregationService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.attendanceService = attendanceService;
        this.timetableService = timetableService;
        this.assignmentService = assignmentService;
        this.submissionService = submissionService;
        this.marksEntryService = marksEntryService;
        this.resultsAggregationService = resultsAggregationService;
        this.notificationRepository = notificationRepository;
        this.reportingAggregationService = reportingAggregationService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);

        if (user.getRole() == Role.PRINCIPAL) {
            model.addAttribute("pendingAccounts",
                    userRepository.findTop5ByStatusOrderByCreatedAtDesc(AccountStatus.PENDING));
            model.addAttribute("pendingCount", reportingAggregationService.pendingApprovalsCount());
            model.addAttribute("totalEnrolledStudents", reportingAggregationService.totalEnrolledStudents());
            model.addAttribute("todaysAttendancePercentage", reportingAggregationService.todaysAttendancePercentageOrNull());
            model.addAttribute("roomsBookedToday", reportingAggregationService.roomsBookedTodayOrNull());
            model.addAttribute("passRatePerSubject", resultsAggregationService.passRatePerSubject());
            model.addAttribute("extraHelpCount", resultsAggregationService.studentsNeedingExtraHelp().size());
        }

        if (user.getRole() == Role.REGISTRAR) {
            model.addAttribute("recentStudents", studentRepository.findTop5ByOrderByEnrollmentDateDesc());
            model.addAttribute("studentsRegisteredCount", studentRepository.count());
            model.addAttribute("awaitingApprovalCount",
                    userRepository.countByRoleInAndStatus(List.of(Role.STUDENT, Role.PARENT), AccountStatus.PENDING));
            model.addAttribute("transferredCount", studentRepository.countByStatus(StudentStatus.TRANSFERRED));
        }

        if (user.getRole() == Role.TEACHER) {
            Teacher teacher = attendanceService.requireTeacher(user);
            String className = teacher.getAssignedClassName();
            if (className != null && !className.isBlank()) {
                model.addAttribute("presentToday", attendanceService.presentTodayCount(className));
                model.addAttribute("classesTaught", 1);
            } else {
                model.addAttribute("classesTaught", 0);
            }
            long ungraded = assignmentService.listForTeacher(teacher.getTeacherId()).stream()
                    .mapToLong(a -> assignmentService.submissionsFor(a.getId()).stream().filter(s -> s.getGrade() == null).count())
                    .sum();
            model.addAttribute("assignmentsToGrade", ungraded);
        }

        if (user.getRole() == Role.STUDENT) {
            model.addAttribute("attendanceSummary", attendanceService.summaryFor(user.getUserId()));
            model.addAttribute("gpa", marksEntryService.gpaFor(user.getUserId()));
            Student student = studentRepository.findById(user.getUserId()).orElse(null);
            if (student != null && student.getClassName() != null && !student.getClassName().isBlank()) {
                model.addAttribute("timetableGrid", timetableService.asGrid(timetableService.slotsForClass(student.getClassName())));
                LocalDate today = LocalDate.now();
                long dueSoon = assignmentService.listForClass(student.getClassName()).stream()
                        .filter(a -> !a.getDueDate().isBefore(today))
                        .filter(a -> submissionService.submissionFor(a.getId(), student.getStudentId()).isEmpty())
                        .count();
                model.addAttribute("assignmentsDueSoon", dueSoon);
            }
            model.addAttribute("timetablePeriods", PrincipalTimetableController.periodRange());
            model.addAttribute("timetableDays", TimetableService.SCHOOL_DAYS);
        }

        if (user.getRole() == Role.PARENT) {
            List<Student> children = studentRepository.findByParent_UserId(user.getUserId());
            OptionalDouble avg = children.stream()
                    .map(c -> attendanceService.summaryFor(c.getStudentId()).percentage())
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average();
            model.addAttribute("childAttendancePercentage", avg.isPresent() ? avg.getAsDouble() : null);
            model.addAttribute("recentAnnouncements",
                    notificationRepository.findTop5ByRecipientUserIdAndSentAtIsNotNullOrderBySentAtDesc(user.getUserId()));
            model.addAttribute("unreadAlertsCount",
                    notificationRepository.countByRecipientUserIdAndReadFalseAndSentAtIsNotNull(user.getUserId()));
        }

        return switch (user.getRole()) {
            case PRINCIPAL -> "dashboard/principal";
            case REGISTRAR -> "dashboard/registrar";
            case TEACHER   -> "dashboard/teacher";
            case STUDENT   -> "dashboard/student";
            case PARENT    -> "dashboard/parent";
        };
    }
}