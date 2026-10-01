package sisa.controller;

import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.TimetableService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class RegistrarTimetableController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TimetableService timetableService;

    public RegistrarTimetableController(UserRepository userRepository, StudentRepository studentRepository,
                                        TimetableService timetableService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.timetableService = timetableService;
    }

    // Read Operation
    // Registrar can view the timetable.
    
    @GetMapping("/registrar/timetable")
    public String view(@RequestParam(required = false) String className, Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");
        model.addAttribute("allClassNames", studentRepository.distinctClassNames());
        model.addAttribute("periods", PrincipalTimetableController.periodRange());
        model.addAttribute("days", TimetableService.SCHOOL_DAYS);
        model.addAttribute("className", className);

        if (className != null && !className.isBlank()) {
            model.addAttribute("grid", timetableService.asGrid(timetableService.slotsForClass(className)));
        }
        return "registrar/timetable-view";
    }
}
