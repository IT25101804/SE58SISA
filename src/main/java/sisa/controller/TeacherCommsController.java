package sisa.controller;

import sisa.entity.Teacher;
import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.AnnouncementService;
import sisa.service.AttendanceService;
import sisa.service.dto.AnnouncementForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/teacher/comms")
public class TeacherCommsController {

    private final UserRepository userRepository;
    private final AttendanceService attendanceService;
    private final AnnouncementService announcementService;

    public TeacherCommsController(UserRepository userRepository, AttendanceService attendanceService,
                                  AnnouncementService announcementService) {
        this.userRepository = userRepository;
        this.attendanceService = attendanceService;
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
        model.addAttribute("teacher", attendanceService.requireTeacher(user));
        return "teacher/comms";
    }

    @GetMapping("/new")
    public String newForm(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        Teacher teacher = attendanceService.requireTeacher(user);
        model.addAttribute("className", teacher.getAssignedClassName());
        model.addAttribute("form", new AnnouncementForm());
        return "teacher/comms-new";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("form") AnnouncementForm form, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        Teacher teacher = attendanceService.requireTeacher(user);
        model.addAttribute("className", teacher.getAssignedClassName());
        try {
            form.setCategory("MESSAGE"); // one kind of message for everyone, no category to choose
            int reached = announcementService.create(form, user);
            model.addAttribute("success", "Sent to " + reached + " recipient(s).");
            model.addAttribute("form", new AnnouncementForm());
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
        }
        return "teacher/comms-new";
    }
}
