package sisa.controller;

import sisa.entity.Teacher;
import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.AccountAdminService;
import sisa.service.TeacherManagementService;
import sisa.service.dto.CreateAccountRequest;
import sisa.service.dto.TeacherAssignmentRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Registrar (and Principal) desk for Teacher accounts and subject/class assignment
 * (System Functions doc: Academic Management -> Registrar "Assign teachers to
 * subjects... change subject-teacher assignments"; User & Access -> Registrar
 * "Create ... teacher ... accounts"). Access is already scoped to
 * PRINCIPAL/REGISTRAR by SecurityConfig's /registrar/** rule.
 */
@Controller
@RequestMapping("/registrar/teachers")
public class RegistrarTeacherController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AccountAdminService accountAdminService;
    private final TeacherManagementService teacherManagementService;

    public RegistrarTeacherController(UserRepository userRepository, StudentRepository studentRepository,
                                      AccountAdminService accountAdminService,
                                      TeacherManagementService teacherManagementService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.accountAdminService = accountAdminService;
        this.teacherManagementService = teacherManagementService;
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

    @GetMapping("/new")
    public String newForm(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        model.addAttribute("request", new CreateAccountRequest());
        return "registrar/teacher-new";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("request") CreateAccountRequest request,
                         Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        try {
            User created = accountAdminService.createTeacher(request, currentUser(authentication));
            model.addAttribute("createdId", created.getUserId());
            model.addAttribute("request", new CreateAccountRequest());
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("request", request);
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

    /** Administration & Reporting Management's "staff-pay reports" (System Functions doc, Principal) needs a salary on file to report on. */
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
