package sisa.controller;

import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.AnnouncementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Update for Communication & Notification Management: edit the subject/body of an
 * announcement or notice that was already posted. Shared by the Principal, Registrar
 * and Teacher (one template, like CommsInboxController) — AnnouncementService enforces
 * that only the Principal or the broadcast's own sender may edit it. Falls under
 * SecurityConfig's anyRequest().authenticated() rule; Students/Parents never send
 * broadcasts, so the ownership check rejects them.
 */
@Controller
@RequestMapping("/comms/broadcasts/{broadcastId}")
public class BroadcastEditController {

    private final UserRepository userRepository;
    private final AnnouncementService announcementService;

    public BroadcastEditController(UserRepository userRepository, AnnouncementService announcementService) {
        this.userRepository = userRepository;
        this.announcementService = announcementService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    /** Where each sender role's "sent" log lives. */
    private static String logUrl(User user) {
        return switch (user.getRole()) {
            case PRINCIPAL -> "/principal/comms";
            case REGISTRAR -> "/registrar/comms";
            case TEACHER -> "/teacher/comms";
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only staff can edit announcements.");
        };
    }

    @GetMapping("/edit")
    public String editForm(@PathVariable String broadcastId, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        model.addAttribute("backUrl", logUrl(user));
        model.addAttribute("broadcast", announcementService.getBroadcastForEdit(broadcastId, user));
        return "comms/broadcast-edit";
    }

    @PostMapping("/edit")
    public String update(@PathVariable String broadcastId,
                         @RequestParam(required = false) String subject, @RequestParam String body,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        String back = logUrl(user);
        try {
            String updated = announcementService.updateBroadcast(broadcastId, subject, body, user);
            redirectAttributes.addFlashAttribute("success", "Updated \"" + updated + "\" for every recipient.");
            return "redirect:" + back;
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/comms/broadcasts/" + broadcastId + "/edit";
        }
    }
}
