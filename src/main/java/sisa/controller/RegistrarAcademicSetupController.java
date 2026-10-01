package sisa.controller;

import sisa.entity.AcademicTerm;
import sisa.entity.Subject;
import sisa.entity.User;
import sisa.repository.AcademicTermRepository;
import sisa.repository.SubjectRepository;
import sisa.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Academic Management module (System Functions doc, Registrar: "Register subjects",
 * "Manage academic calendar/terms"). Already scoped to PRINCIPAL/REGISTRAR by
 * SecurityConfig's /registrar/** rule.
 */
@Controller
@RequestMapping("/registrar/academic-setup")
public class RegistrarAcademicSetupController {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicTermRepository academicTermRepository;

    public RegistrarAcademicSetupController(UserRepository userRepository, SubjectRepository subjectRepository,
                                            AcademicTermRepository academicTermRepository) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.academicTermRepository = academicTermRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String view(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "academic-setup");
        model.addAttribute("subjects", subjectRepository.findAllByOrderByNameAsc());
        model.addAttribute("terms", academicTermRepository.findAllByOrderByStartDateDesc());
        return "registrar/academic-setup";
    }

    @PostMapping("/subjects")
    public String addSubject(@RequestParam String name, @RequestParam(required = false) String code,
                             RedirectAttributes redirectAttributes) {
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Subject name can't be empty.");
            return "redirect:/registrar/academic-setup";
        }
        if (subjectRepository.existsByNameIgnoreCase(name.trim())) {
            redirectAttributes.addFlashAttribute("error", "\"" + name.trim() + "\" is already registered.");
            return "redirect:/registrar/academic-setup";
        }
        Subject subject = new Subject();
        subject.setName(name.trim());
        subject.setCode(code != null ? code.trim() : null);
        subjectRepository.save(subject);
        redirectAttributes.addFlashAttribute("success", "Subject \"" + subject.getName() + "\" registered.");
        return "redirect:/registrar/academic-setup";
    }

    @PostMapping("/terms")
    public String addTerm(@RequestParam String name, @RequestParam String startDate, @RequestParam String endDate,
                          RedirectAttributes redirectAttributes) {
        try {
            AcademicTerm term = new AcademicTerm();
            term.setName(name.trim());
            term.setStartDate(LocalDate.parse(startDate));
            term.setEndDate(LocalDate.parse(endDate));
            academicTermRepository.save(term);
            redirectAttributes.addFlashAttribute("success", "Term \"" + term.getName() + "\" added.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Couldn't add term: " + ex.getMessage());
        }
        return "redirect:/registrar/academic-setup";
    }

    @PostMapping("/terms/{id}/set-current")
    @Transactional
    public String setCurrent(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AcademicTerm term = academicTermRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such term: " + id));
        for (AcademicTerm other : academicTermRepository.findAllByCurrentTrue()) {
            other.setCurrent(false);
            academicTermRepository.save(other);
        }
        term.setCurrent(true);
        academicTermRepository.save(term);
        redirectAttributes.addFlashAttribute("success", "\"" + term.getName() + "\" is now the current term.");
        return "redirect:/registrar/academic-setup";
    }

    /** Subjects carry no foreign key elsewhere (TimetableSlot/Exam store subject as free text — see Subject's javadoc), so this is a plain delete. */
    @PostMapping("/subjects/{id}/delete")
    public String deleteSubject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Subject subject = subjectRepository.findById(id).orElse(null);
        if (subject == null) {
            redirectAttributes.addFlashAttribute("error", "No such subject.");
        } else {
            subjectRepository.delete(subject);
            redirectAttributes.addFlashAttribute("success", "Subject \"" + subject.getName() + "\" deleted.");
        }
        return "redirect:/registrar/academic-setup";
    }

    /** Refuses to delete whichever term is flagged current, so the school is never left without one — set another term current first. */
    @PostMapping("/terms/{id}/delete")
    public String deleteTerm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AcademicTerm term = academicTermRepository.findById(id).orElse(null);
        if (term == null) {
            redirectAttributes.addFlashAttribute("error", "No such term.");
        } else if (term.isCurrent()) {
            redirectAttributes.addFlashAttribute("error",
                    "\"" + term.getName() + "\" is the current term — set another term as current first, then delete it.");
        } else {
            academicTermRepository.delete(term);
            redirectAttributes.addFlashAttribute("success", "\"" + term.getName() + "\" deleted.");
        }
        return "redirect:/registrar/academic-setup";
    }
}
