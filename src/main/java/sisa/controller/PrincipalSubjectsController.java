package sisa.controller;

import sisa.entity.AcademicTerm;
import sisa.entity.Subject;
import sisa.entity.User;
import sisa.repository.AcademicTermRepository;
import sisa.repository.ExamRepository;
import sisa.repository.SubjectRepository;
import sisa.repository.UserRepository;
import sisa.service.AcademicSetupService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Principal's Subjects & Terms page. Subjects and terms are independent lists:
 * a subject isn't tied to a term. Terms are chosen when a teacher creates an exam.
 */
@Controller
@RequestMapping("/principal/subjects")
public class PrincipalSubjectsController {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicTermRepository academicTermRepository;
    private final ExamRepository examRepository;
    private final AcademicSetupService academicSetupService;

    public PrincipalSubjectsController(UserRepository userRepository, SubjectRepository subjectRepository,
                                       AcademicTermRepository academicTermRepository, ExamRepository examRepository,
                                       AcademicSetupService academicSetupService) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.academicTermRepository = academicTermRepository;
        this.examRepository = examRepository;
        this.academicSetupService = academicSetupService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String view(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "subjects");
        model.addAttribute("subjects", subjectRepository.findAllByOrderByNameAsc());
        model.addAttribute("terms", academicTermRepository.findAllByOrderByStartDateDesc());
        return "principal/subjects";
    }

    // ---------- subjects ----------

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

    @PostMapping("/{id}/edit")
    public String updateSubject(@PathVariable Long id, @RequestParam String name, @RequestParam(required = false) String code,
                                RedirectAttributes redirectAttributes) {
        try {
            int moved = academicSetupService.updateSubject(id, name, code);
            redirectAttributes.addFlashAttribute("success", "Subject \"" + name.trim() + "\" updated"
                    + (moved > 0 ? " (" + moved + " exams, timetable periods, assignments and teacher specialties now use the new name)." : "."));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
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

    // ---------- terms ----------

    @PostMapping("/terms")
    public String addTerm(@RequestParam String name, @RequestParam String startDate, @RequestParam String endDate,
                          RedirectAttributes redirectAttributes) {
        try {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Term name can't be empty.");
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            if (end.isBefore(start)) throw new IllegalArgumentException("The end date must be after the start date.");
            AcademicTerm term = new AcademicTerm();
            term.setName(name.trim());
            term.setStartDate(start);
            term.setEndDate(end);
            academicTermRepository.save(term);
            redirectAttributes.addFlashAttribute("success", "Term \"" + term.getName() + "\" added.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Couldn't add term: " + ex.getMessage());
        }
        return "redirect:/principal/subjects";
    }

    @PostMapping("/terms/{id}/edit")
    public String updateTerm(@PathVariable Long id, @RequestParam String name, @RequestParam String startDate,
                             @RequestParam String endDate, RedirectAttributes redirectAttributes) {
        try {
            AcademicTerm term = academicSetupService.updateTerm(id, name, startDate, endDate);
            redirectAttributes.addFlashAttribute("success", "Term \"" + term.getName() + "\" updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Couldn't update term: " + ex.getMessage());
        }
        return "redirect:/principal/subjects";
    }

    @PostMapping("/terms/{id}/set-current")
    @Transactional
    public String setCurrentTerm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AcademicTerm term = academicTermRepository.findById(id).orElse(null);
        if (term == null) {
            redirectAttributes.addFlashAttribute("error", "No such term.");
            return "redirect:/principal/subjects";
        }
        for (AcademicTerm other : academicTermRepository.findAllByCurrentTrue()) {
            other.setCurrent(false);
            academicTermRepository.save(other);
        }
        term.setCurrent(true);
        academicTermRepository.save(term);
        redirectAttributes.addFlashAttribute("success", "\"" + term.getName() + "\" is now the current term.");
        return "redirect:/principal/subjects";
    }

    @PostMapping("/terms/{id}/delete")
    public String deleteTerm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AcademicTerm term = academicTermRepository.findById(id).orElse(null);
        if (term == null) {
            redirectAttributes.addFlashAttribute("error", "No such term.");
        } else if (term.isCurrent()) {
            redirectAttributes.addFlashAttribute("error",
                    "\"" + term.getName() + "\" is the current term. Set another term as current first, then delete it.");
        } else if (examRepository.existsByTerm_Id(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "\"" + term.getName() + "\" has exams recorded in it, so it can't be deleted.");
        } else {
            academicTermRepository.delete(term);
            redirectAttributes.addFlashAttribute("success", "Term \"" + term.getName() + "\" deleted.");
        }
        return "redirect:/principal/subjects";
    }
}
