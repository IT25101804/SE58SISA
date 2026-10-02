package sisa.controller;

import sisa.entity.Role;
import sisa.entity.Student;
import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.MarksEntryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ParentAcademicController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final MarksEntryService marksEntryService;

    public ParentAcademicController(UserRepository userRepository, StudentRepository studentRepository,
                                    MarksEntryService marksEntryService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.marksEntryService = marksEntryService;
    }

    @GetMapping("/parent/child-results")
    public String childResults(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        if (user.getRole() != Role.PARENT) {
            model.addAttribute("notice", "Only Parent accounts have children linked here.");
            return "parent/child-results";
        }
        List<Student> children = studentRepository.findByParent_UserId(user.getUserId());
        List<ChildResults> childData = children.stream()
                .map(child -> new ChildResults(child, marksEntryService.reportCardFor(child.getStudentId()),
                        marksEntryService.monthlyGpaTrend(child.getStudentId())))
                .toList();
        model.addAttribute("children", childData);
        return "parent/child-results";
    }

    public record ChildResults(Student student, MarksEntryService.ReportCard reportCard,
                               List<MarksEntryService.GpaTrendPoint> trend) {}
}
