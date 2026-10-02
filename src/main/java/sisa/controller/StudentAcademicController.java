package sisa.controller;

import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.MarksEntryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StudentAcademicController {

    private final UserRepository userRepository;
    private final MarksEntryService marksEntryService;

    public StudentAcademicController(UserRepository userRepository, MarksEntryService marksEntryService) {
        this.userRepository = userRepository;
        this.marksEntryService = marksEntryService;
    }

    @GetMapping("/student/results")
    public String results(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        if (user.getRole() != Role.STUDENT) {
            model.addAttribute("notice", "Only Student accounts have results here.");
            return "student/results";
        }
        model.addAttribute("reportCard", marksEntryService.reportCardFor(user.getUserId()));
        return "student/results";
    }
}
