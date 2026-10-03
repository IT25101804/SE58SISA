package sisa.controller;

import sisa.entity.Teacher;
import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.SubjectRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.UserRepository;
import sisa.service.AccountAdminService;
import sisa.service.AccountDeletionService;
import sisa.service.TeacherManagementService;
import sisa.service.dto.CreateAccountRequest;
import sisa.service.dto.TeacherAssignmentRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/registrar/teachers")
public class RegistrarTeacherController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AccountAdminService accountAdminService;
    private final TeacherManagementService teacherManagementService;
    private final AccountDeletionService accountDeletionService;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;

    public RegistrarTeacherController(UserRepository userRepository, StudentRepository studentRepository,
                                      AccountAdminService accountAdminService,
                                      TeacherManagementService teacherManagementService,
                                      AccountDeletionService accountDeletionService,
                                      SubjectRepository subjectRepository, TeacherRepository teacherRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.accountAdminService = accountAdminService;
        this.teacherManagementService = teacherManagementService;
        this.accountDeletionService = accountDeletionService;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        model.addAttribute("teachers", teacherManagementService.listAll());
        return "registrar/teachers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            accountDeletionService.deleteTeacher(id, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Teacher account " + id + " was deleted.");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/registrar/teachers";
    }

    @GetMapping("/new")
    public String newForm(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        model.addAttribute("request", new CreateAccountRequest());
        model.addAttribute("subjects", subjectRepository.findAllByOrderByNameAsc());
        return "registrar/teacher-new";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("request") CreateAccountRequest request,
                         @RequestParam(required = false) String subjectSpecialty,
                         Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        model.addAttribute("subjects", subjectRepository.findAllByOrderByNameAsc());
        try {
            boolean hasSpecialty = subjectSpecialty != null && !subjectSpecialty.isBlank();
            if (hasSpecialty && !subjectRepository.existsByNameIgnoreCase(subjectSpecialty.trim())) {
                throw new IllegalArgumentException("Choose one of the registered subjects as the subject specialty.");
            }
            User created = accountAdminService.createTeacher(request, currentUser(authentication));
            if (hasSpecialty) {
                teacherRepository.findById(created.getUserId()).ifPresent(t -> {
                    t.setSubjectSpecialty(subjectSpecialty.trim());
                    teacherRepository.save(t);
                });
            }
            model.addAttribute("createdId", created.getUserId());
            model.addAttribute("request", new CreateAccountRequest());
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("request", request);
            model.addAttribute("selectedSpecialty", subjectSpecialty);
        }
        return "registrar/teacher-new";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        Teacher teacher = teacherManagementService.getOrThrow(id);
        model.addAttribute("teacher", teacher);
        model.addAttribute("allClassNames", studentRepository.distinctClassNames());
        model.addAttribute("subjects", subjectRepository.findAllByOrderByNameAsc());

        TeacherAssignmentRequest form = new TeacherAssignmentRequest();
        form.setSubjectSpecialty(teacher.getSubjectSpecialty());
        form.setJoiningYear(teacher.getJoiningYear());
        form.setClassTeacher(teacher.isClassTeacher());
        form.setAssignedClassName(teacher.getAssignedClassName());
        model.addAttribute("form", form);
        return "registrar/teacher-detail";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable String id, @ModelAttribute("form") TeacherAssignmentRequest request,
                       Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            teacherManagementService.updateAssignment(id, request, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Assignment updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/registrar/teachers/" + id;
    }

    @PostMapping("/{id}/salary")
    public String updateSalary(@PathVariable String id, @RequestParam(required = false) Double monthlySalary,
                               RedirectAttributes redirectAttributes) {
        try {
            teacherManagementService.updateSalary(id, monthlySalary);
            redirectAttributes.addFlashAttribute("success", "Salary updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/registrar/teachers/" + id;
    }
}
