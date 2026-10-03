package sisa.controller;

import sisa.entity.Subject;
import sisa.entity.User;
import sisa.repository.SubjectRepository;
import sisa.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/principal/subjects")
public class PrincipalSubjectsController {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;

    public PrincipalSubjectsController(UserRepository userRepository, SubjectRepository subjectRepository) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String view(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "subjects");
        model.addAttribute("subjects", subjectRepository.findAllByOrderByNameAsc());
        return "principal/subjects";
    }

    @PostMapping
    public String addSubject(@RequestParam String name, @RequestParam(required = false) String code,
                             RedirectAttributes redirectAttributes) {
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Subject name can't be empty.");
            return "redirect:/principal/subjects";
        }
        if (subjectRepository.existsByNameIgnoreCase(name.trim())) {
            redirectAttributes.addFlashAttribute("error", "\"" + name.trim() + "\" is already registered.");
            return "redirect:/principal/subjects";
        }
        Subject subject = new Subject();
        subject.setName(name.trim());
        subject.setCode(code != null && !code.isBlank() ? code.trim() : null);
        subjectRepository.save(subject);
        redirectAttributes.addFlashAttribute("success", "Subject \"" + subject.getName() + "\" registered.");
        return "redirect:/principal/subjects";
    }

    @PostMapping("/{id}/delete")
    public String deleteSubject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Subject subject = subjectRepository.findById(id).orElse(null);
        if (subject == null) {
            redirectAttributes.addFlashAttribute("error", "No such subject.");
        } else {
            subjectRepository.delete(subject);
            redirectAttributes.addFlashAttribute("success", "Subject \"" + subject.getName() + "\" deleted.");
        }
        return "redirect:/principal/subjects";
    }
}
