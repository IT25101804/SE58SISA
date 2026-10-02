package sisa.controller;

import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.BookingService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/principal/resources/approvals")
public class PrincipalBookingApprovalController {

    private final UserRepository userRepository;
    private final BookingService bookingService;

    public PrincipalBookingApprovalController(UserRepository userRepository, BookingService bookingService) {
        this.userRepository = userRepository;
        this.bookingService = bookingService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String pending(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "resources");
        model.addAttribute("pendingBookings", bookingService.pendingApprovals());
        return "principal/resource-approvals";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            bookingService.approve(id, currentUser(authentication).getUserId());
            redirectAttributes.addFlashAttribute("success", "Booking approved.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/principal/resources/approvals";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            bookingService.reject(id, currentUser(authentication).getUserId());
            redirectAttributes.addFlashAttribute("success", "Booking rejected.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/principal/resources/approvals";
    }
}
