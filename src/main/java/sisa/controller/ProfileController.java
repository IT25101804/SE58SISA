package sisa.controller;

import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.AuditLogService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public ProfileController(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "profile");
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String fullName,
                                @RequestParam String email,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        user.setFullName(fullName);
        user.setEmail(email);
        userRepository.save(user);
        auditLogService.log(user.getUserId(), user.getUserId(), "PROFILE_UPDATE", "Updated name/email");
        redirectAttributes.addFlashAttribute("success", "Profile updated.");
        return "redirect:/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Current password is incorrect.");
            return "redirect:/profile";
        }
        if (newPassword == null || newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("error", "New password must be at least 8 characters.");
            return "redirect:/profile";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditLogService.log(user.getUserId(), user.getUserId(), "PASSWORD_RESET", "Self-service password change");
        redirectAttributes.addFlashAttribute("success", "Password changed.");
        return "redirect:/profile";
    }
}