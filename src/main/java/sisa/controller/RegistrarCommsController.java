package sisa.controller;

import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.AnnouncementService;
import sisa.service.dto.AnnouncementForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/registrar/comms")
public class RegistrarCommsController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AnnouncementService announcementService;

    public RegistrarCommsController(UserRepository userRepository, StudentRepository studentRepository,
                                    AnnouncementService announcementService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.announcementService = announcementService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String log(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        model.addAttribute("broadcasts", announcementService.broadcastsBySender(user.getUserId()));
        return "registrar/comms";
    }

    @GetMapping("/new")
    public String newForm(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "comm");
        model.addAttribute("allClassNames", studentRepository.distinctClassNames());
        model.addAttribute("allTeachers", announcementService.activeTeachers());
        model.addAttribute("form", new AnnouncementForm());
        return "registrar/comms-new";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("form") AnnouncementForm form, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        model.addAttribute("allClassNames", studentRepository.distinctClassNames());
        model.addAttribute("allTeachers", announcementService.activeTeachers());
        try {
            int reached = announcementService.create(form, user);
            model.addAttribute("success", "Sent to " + reached + " recipient(s).");
            model.addAttribute("form", new AnnouncementForm());
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
        }
        return "registrar/comms-new";
    }
}
