package sisa.controller;

import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StudentProfileController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    public StudentProfileController(UserRepository userRepository, StudentRepository studentRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping("/student/profile")
    public String ownProfile(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "students");

        if (user.getRole() != Role.STUDENT) {
            model.addAttribute("notice", "Only Student accounts have a profile here.");
            return "student/profile";
        }
        model.addAttribute("student", studentRepository.findById(user.getUserId()).orElseThrow());
        return "student/profile";
    }

    @GetMapping("/parent/child")
    public String children(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "students");

        if (user.getRole() != Role.PARENT) {
            model.addAttribute("notice", "Only Parent accounts have children linked here.");
            return "parent/child";
        }
        model.addAttribute("children", studentRepository.findByParent_UserId(user.getUserId()));
        return "parent/child";
    }
}
