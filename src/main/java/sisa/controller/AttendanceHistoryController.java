package sisa.controller;

import sisa.entity.AttendanceStatus;
import sisa.entity.Role;
import sisa.entity.Student;
import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.AttendanceService;
import sisa.service.dto.AttendanceCorrectionForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class AttendanceHistoryController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AttendanceService attendanceService;

    public AttendanceHistoryController(UserRepository userRepository, StudentRepository studentRepository,
                                       AttendanceService attendanceService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.attendanceService = attendanceService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping("/student/attendance")
    public String ownHistory(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "attendance");

        if (user.getRole() != Role.STUDENT) {
            model.addAttribute("notice", "Only Student accounts have attendance history here.");
            return "student/attendance";
        }
        model.addAttribute("records", attendanceService.historyFor(user.getUserId()));
        model.addAttribute("summary", attendanceService.summaryFor(user.getUserId()));
        model.addAttribute("correctionRequests", attendanceService.requestsByStudent(user.getUserId()));
        model.addAttribute("correctionForm", new AttendanceCorrectionForm());
        return "student/attendance";
    }

    @PostMapping("/student/attendance/correction-request")
    public String requestCorrection(@ModelAttribute AttendanceCorrectionForm form,
                                    Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            attendanceService.requestCorrection(user.getUserId(), form.getAttendanceRecordId(),
                    AttendanceStatus.valueOf(form.getRequestedStatus()), form.getReason());
            redirectAttributes.addFlashAttribute("success", "Correction request submitted for your teacher to review.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/student/attendance";
    }

    @GetMapping("/parent/child-attendance")
    public String childrenHistory(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "attendance");

        if (user.getRole() != Role.PARENT) {
            model.addAttribute("notice", "Only Parent accounts have children linked here.");
            return "parent/child-attendance";
        }
        List<Student> children = studentRepository.findByParent_UserId(user.getUserId());
        List<AttendanceService.ChildAttendance> childData = children.stream()
                .map(child -> new AttendanceService.ChildAttendance(
                        child, attendanceService.historyFor(child.getStudentId()), attendanceService.summaryFor(child.getStudentId())))
                .toList();
        model.addAttribute("children", childData);
        return "parent/child-attendance";
    }
}
