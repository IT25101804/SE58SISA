package sisa.controller;

import sisa.entity.ResourceType;
import sisa.entity.User;
import sisa.repository.ResourceRepository;
import sisa.repository.StudentRepository;
import sisa.repository.SubjectRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.UserRepository;
import sisa.service.TimetableService;
import sisa.service.dto.TimetableSlotForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/principal/timetable")
public class PrincipalTimetableController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ResourceRepository resourceRepository;
    private final TimetableService timetableService;
    private final SubjectRepository subjectRepository;

    public PrincipalTimetableController(UserRepository userRepository, StudentRepository studentRepository,
                                        TeacherRepository teacherRepository, ResourceRepository resourceRepository,
                                        TimetableService timetableService,
                                       SubjectRepository subjectRepository) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.resourceRepository = resourceRepository;
        this.timetableService = timetableService;
        this.subjectRepository = subjectRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String builder(@RequestParam(required = false) String className, @RequestParam(required = false) Long edit,
                          Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "timetable");

        model.addAttribute("allClassNames", studentRepository.distinctClassNames());

        model.addAttribute("teachers", teacherRepository.findAll());
        model.addAttribute("subjects", subjectRepository.findAllByOrderByNameAsc());
        model.addAttribute("bookableRooms", resourceRepository.findAllByOrderByTypeAscNameAsc().stream()
                .filter(r -> r.getType() != ResourceType.EQUIPMENT).toList());
        model.addAttribute("periods", periodRange());
        model.addAttribute("days", TimetableService.SCHOOL_DAYS);
        model.addAttribute("className", className);

        if (className != null && !className.isBlank()) {
            model.addAttribute("grid", timetableService.asGrid(timetableService.slotsForClass(className)));
            TimetableSlotForm form = new TimetableSlotForm();
            if (edit != null) {
                timetableService.slotsForClass(className).stream()
                        .filter(s -> s.getId().equals(edit))
                        .findFirst()
                        .ifPresent(s -> {
                            form.setSlotId(s.getId());
                            form.setClassName(s.getClassName());
                            form.setSubject(s.getSubject());
                            form.setTeacherId(s.getTeacher().getTeacherId());
                            form.setDayOfWeek(s.getDayOfWeek().name());
                            form.setPeriodNumber(s.getPeriodNumber());
                            form.setRoomResourceId(s.getRoom() == null ? null : s.getRoom().getId());
                        });
            }
            model.addAttribute("form", form);
        }
        return "principal/timetable";
    }

    @PostMapping("/slot")
    public String saveSlot(@ModelAttribute("form") TimetableSlotForm form, Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            // Only subjects registered under Subjects & Terms can be timetabled.
            if (form.getSubject() == null || !subjectRepository.existsByNameIgnoreCase(form.getSubject().trim())) {
                throw new IllegalArgumentException("Choose one of the registered subjects.");
            }
            boolean editing = form.getSlotId() != null;
            timetableService.saveSlot(form);
            redirectAttributes.addFlashAttribute("success", form.getSubject() + (editing ? " updated for " : " added for ")
                    + form.getDayOfWeek() + " period " + form.getPeriodNumber() + ".");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            if (form.getSlotId() != null) {
                return "redirect:/principal/timetable?className=" + form.getClassName() + "&edit=" + form.getSlotId();
            }
        }
        return "redirect:/principal/timetable?className=" + form.getClassName();
    }

    @PostMapping("/slot/{id}/delete")
    public String deleteSlot(@PathVariable Long id, @RequestParam String className, RedirectAttributes redirectAttributes) {

        timetableService.deleteSlot(id);
        redirectAttributes.addFlashAttribute("success", "Slot removed.");
        return "redirect:/principal/timetable?className=" + className;
    }

    static java.util.List<Integer> periodRange() {
        java.util.List<Integer> periods = new java.util.ArrayList<>();
        for (int i = 1; i <= TimetableService.MAX_PERIODS; i++) periods.add(i);
        return periods;
    }
}
