package sisa.controller;

import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.AnnouncementService;
import sisa.service.ParentTeacherContactService;
import sisa.service.dto.AnnouncementForm;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * "New Message" for students and parents: the same inbox messages every other role sends,
 * addressed to one of their (or their child's) teachers, chosen by name and subject.
 */
@Controller
@RequestMapping("/comms/new")
public class StudentParentMessageController {

    private final UserRepository userRepository;
    private final AnnouncementService announcementService;
    private final ParentTeacherContactService parentTeacherContactService;

    public StudentParentMessageController(UserRepository userRepository, AnnouncementService announcementService,
                                          ParentTeacherContactService parentTeacherContactService) {
        this.userRepository = userRepository;
        this.announcementService = announcementService;
        this.parentTeacherContactService = parentTeacherContactService;
    }

    private User studentOrParent(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getRole() != Role.STUDENT && user.getRole() != Role.PARENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This page is for students and parents.");
        }
        return user;
    }

    private void addPageData(User user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "comm");
        model.addAttribute("teacherOptions", user.getRole() == Role.PARENT
                ? parentTeacherContactService.teachersForParent(user.getUserId())
                : parentTeacherContactService.teachersForStudent(user.getUserId()));
    }

    @GetMapping
    public String form(Authentication authentication, Model model) {
        addPageData(studentOrParent(authentication), model);
        model.addAttribute("form", new AnnouncementForm());
        return "comms/compose";
    }

    @PostMapping
    public String send(@ModelAttribute("form") AnnouncementForm form, Authentication authentication, Model model) {
        User user = studentOrParent(authentication);
        addPageData(user, model);
        form.setCategory("MESSAGE");
        form.setTargetScope("TEACHER");
        form.setScheduledFor(null);
        try {
            announcementService.create(form, user);
            model.addAttribute("success", "Message sent.");
            model.addAttribute("form", new AnnouncementForm());
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
        }
        return "comms/compose";
    }
}
