package sisa.controller;

import sisa.entity.User;
import sisa.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Controller
public class ModuleController {

    private final UserRepository userRepository;

    private static final Map<String, String[]> MODULES = Map.of(
        "academic",   new String[]{"Academic Management", "bi-mortarboard-fill", "feature/05-academic-management"},
        "students",   new String[]{"Student Information Management", "bi-person-vcard-fill", "feature/02-student-information"},
        "attendance", new String[]{"Attendance Management", "bi-calendar2-check-fill", "feature/03-attendance-management"},
        "timetable",  new String[]{"Timetable & Assignment Management", "bi-clock-fill", "feature/04-timetable-assignment"},
        "comm",       new String[]{"Communication & Notification Management", "bi-megaphone-fill", "feature/06-communication-notification"},
        "reports",    new String[]{"Administration & Reporting Management", "bi-bar-chart-fill", "feature/07-administration-reporting"},
        "access",     new String[]{"User & Access Management", "bi-shield-lock-fill", "feature/01-user-access-management"},
        "resources",  new String[]{"School Resources & Facilities Management", "bi-building-fill", "feature/08-resources-facilities"}
    );

    public ModuleController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/modules/{key}")
    public String comingSoon(@PathVariable String key, Authentication authentication, Model model) {
        String[] info = MODULES.get(key);
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such module: " + key);
        }
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("activeItem", key);
        model.addAttribute("moduleName", info[0]);
        model.addAttribute("moduleIcon", info[1]);
        model.addAttribute("branchName", info[2]);
        return "modules/coming-soon";
    }
}
