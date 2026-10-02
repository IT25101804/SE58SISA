package sisa.controller;

import sisa.entity.*;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.AssignmentService;
import sisa.service.SubmissionService;
import sisa.service.TimetableService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class ParentAssignmentController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TimetableService timetableService;
    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;

    public ParentAssignmentController(UserRepository userRepository, StudentRepository studentRepository,
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

    @GetMapping("/parent/timetable")
    public String timetable(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");
        model.addAttribute("periods", PrincipalTimetableController.periodRange());
        model.addAttribute("days", TimetableService.SCHOOL_DAYS);

        if (user.getRole() != Role.PARENT) {
            model.addAttribute("notice", "Only Parent accounts have children linked here.");
            return "parent/timetable";
        }
        List<Student> children = studentRepository.findByParent_UserId(user.getUserId());
        model.addAttribute("children", children);
        model.addAttribute("grids", children.stream()
                .collect(Collectors.toMap(Student::getStudentId, c -> timetableService.asGrid(timetableService.slotsForClass(c.getClassName())))));
        return "parent/timetable";
    }

    @GetMapping("/parent/assignments")
    public String assignments(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");

        if (user.getRole() != Role.PARENT) {
            model.addAttribute("notice", "Only Parent accounts have children linked here.");
            return "parent/assignments";
        }
        List<Student> children = studentRepository.findByParent_UserId(user.getUserId());
        List<ChildAssignments> childData = children.stream().map(this::assignmentsFor).toList();
        model.addAttribute("children", childData);
        return "parent/assignments";
    }

    private ChildAssignments assignmentsFor(Student child) {
        List<Assignment> assignments = assignmentService.listForClass(child.getClassName());
        Map<Long, AssignmentSubmission> submissions = assignments.stream()
                .map(a -> submissionService.submissionFor(a.getId(), child.getStudentId()))
                .filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toMap(s -> s.getAssignment().getId(), s -> s));
        return new ChildAssignments(child, assignments, submissions);
    }

    public record ChildAssignments(Student student, List<Assignment> assignments,
                                   Map<Long, AssignmentSubmission> submissions) {}
}
