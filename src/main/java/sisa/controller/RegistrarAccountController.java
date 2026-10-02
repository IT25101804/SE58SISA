package sisa.controller;

import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.AccountAdminService;
import sisa.service.dto.CreateAccountRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Standalone Parent account creation (System Functions doc, User & Access:
 * Registrar "Create student, teacher, and parent accounts" — listed as three
 * separate capabilities). Registering a student already auto-creates/links a
 * Parent (StudentRegistrationService); this covers creating one on its own,
 * e.g. ahead of linking them to a student record. Access is already scoped to
 * PRINCIPAL/REGISTRAR by SecurityConfig's /registrar/** rule.
 */
@Controller
@RequestMapping("/registrar/accounts")
public class RegistrarAccountController {

    private final UserRepository userRepository;
    private final AccountAdminService accountAdminService;

    public RegistrarAccountController(UserRepository userRepository, AccountAdminService accountAdminService) {
        this.userRepository = userRepository;
        this.accountAdminService = accountAdminService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping("/new-parent")
    public String newForm(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "students");
        model.addAttribute("request", new CreateAccountRequest());
        return "registrar/new-parent";
    }

    @PostMapping("/new-parent")
    public String create(@ModelAttribute("request") CreateAccountRequest request,
                         Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "students");
        try {
            User created = accountAdminService.createParent(request, currentUser(authentication));
            model.addAttribute("createdId", created.getUserId());
            model.addAttribute("request", new CreateAccountRequest());
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("request", request);
        }
        return "registrar/new-parent";
    }
}
