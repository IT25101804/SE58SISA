package sisa.controller;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.AssignmentService;
import sisa.service.SubmissionService;
import sisa.service.TimetableService;
import sisa.service.dto.SubmissionForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Controller
public class StudentAssignmentController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TimetableService timetableService;
    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;

    public StudentAssignmentController(UserRepository userRepository, StudentRepository studentRepository,
                                       TimetableService timetableService, AssignmentService assignmentService,
                                       SubmissionService submissionService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.timetableService = timetableService;
        this.assignmentService = assignmentService;
        this.submissionService = submissionService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping("/student/timetable")
    public String myTimetable(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");
        model.addAttribute("periods", PrincipalTimetableController.periodRange());
        model.addAttribute("days", TimetableService.SCHOOL_DAYS);

        if (user.getRole() != Role.STUDENT) {
            model.addAttribute("notice", "Only Student accounts have a class timetable here.");
            return "student/timetable";
        }
        Student student = studentRepository.findById(user.getUserId()).orElseThrow();
        model.addAttribute("className", student.getClassName());
        model.addAttribute("grid", timetableService.asGrid(timetableService.slotsForClass(student.getClassName())));
        return "student/timetable";
    }

    @GetMapping("/student/assignments")
    public String list(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");

        if (user.getRole() != Role.STUDENT) {
            model.addAttribute("notice", "Only Student accounts have assignments here.");
            return "student/assignments";
        }
        Student student = studentRepository.findById(user.getUserId()).orElseThrow();
        List<Assignment> assignments = assignmentService.listForClass(student.getClassName());
        Map<Long, AssignmentSubmission> mySubmissions = assignments.stream()
                .map(a -> submissionService.submissionFor(a.getId(), student.getStudentId()))
                .filter(java.util.Optional::isPresent).map(java.util.Optional::get)
                .collect(Collectors.toMap(s -> s.getAssignment().getId(), s -> s));

        model.addAttribute("assignments", assignments);
        model.addAttribute("mySubmissions", mySubmissions);
        return "student/assignments";
    }

    @GetMapping("/student/assignments/{id}")
    public String detail(@PathVariable Long id, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");

        Assignment assignment = assignmentService.getOrThrow(id);
        model.addAttribute("assignment", assignment);
        model.addAttribute("form", new SubmissionForm());
        model.addAttribute("isStudent", user.getRole() == Role.STUDENT);

        if (user.getRole() == Role.STUDENT) {
            Student student = studentRepository.findById(user.getUserId()).orElseThrow();
            model.addAttribute("submission", submissionService.submissionFor(id, student.getStudentId()).orElse(null));
        }
        return "student/assignment-detail";
    }

    @PostMapping("/student/assignments/{id}/submit")
    public String submit(@PathVariable Long id, @ModelAttribute("form") SubmissionForm form,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            Student student = studentRepository.findById(user.getUserId()).orElseThrow();
            Assignment assignment = assignmentService.getOrThrow(id);
            AssignmentSubmission submission = submissionService.submit(assignment, student, form.getFileUrlOrText());
            redirectAttributes.addFlashAttribute("success",
                    submission.getStatus() == SubmissionStatus.LATE
                            ? "Submitted — marked Late since the due date has passed."
                            : "Submitted on time.");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/assignments/" + id;
    }
}
