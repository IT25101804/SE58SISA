package sisa.controller;

import sisa.entity.ResourceType;
import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.BookingService;
import sisa.service.dto.ResourceForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Resource catalog management (report FR-13, business rule 2): the Registrar assigns
 * classrooms and keeps records up to date; the Principal can too. Access is already
 * scoped to PRINCIPAL/REGISTRAR by SecurityConfig's existing /registrar/** rule —
 * no security changes needed for this controller.
 */
@Controller
@RequestMapping("/registrar/resources")
public class ResourceCatalogController {

    private final UserRepository userRepository;
    private final BookingService bookingService;

    public ResourceCatalogController(UserRepository userRepository, BookingService bookingService) {
        this.userRepository = userRepository;
        this.bookingService = bookingService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String catalog(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "resources");
        model.addAttribute("resources", bookingService.allResources());
        model.addAttribute("resourceTypes", ResourceType.values());
        model.addAttribute("classEnrollment", bookingService.classroomEnrollmentCounts());
        model.addAttribute("form", new ResourceForm());
        return "registrar/resource-catalog";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("form") ResourceForm form,
                       @RequestParam(name = "studentBookable", defaultValue = "false") boolean studentBookable,
                       RedirectAttributes redirectAttributes) {
        try {
            bookingService.saveResource(form, studentBookable);
            redirectAttributes.addFlashAttribute("success", form.getName() + " saved.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/registrar/resources";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String name = bookingService.deleteResource(id);
            redirectAttributes.addFlashAttribute("success", name + " deleted.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/registrar/resources";
    }
}
