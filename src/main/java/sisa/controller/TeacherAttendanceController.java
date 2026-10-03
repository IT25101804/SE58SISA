package sisa.controller;

import sisa.entity.*;
import sisa.entity.*;
import sisa.repository.UserRepository;
import sisa.service.AttendanceService;
import sisa.service.dto.AttendanceMarkForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/teacher/attendance")
public class TeacherAttendanceController {

    private final UserRepository userRepository;
    private final AttendanceService attendanceService;

    public TeacherAttendanceController(UserRepository userRepository, AttendanceService attendanceService) {
        this.userRepository = userRepository;
        this.attendanceService = attendanceService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String view(@RequestParam(required = false) String className,
                       @RequestParam(required = false) String date,
                       @RequestParam(defaultValue = "false") boolean edit,
                       Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "attendance");

        LocalDate today = LocalDate.now();
        LocalDate resolvedDate = parseDateOrToday(date, today);
        model.addAttribute("date", resolvedDate.toString());
        model.addAttribute("todayIso", today.toString());
        model.addAttribute("isToday", resolvedDate.equals(today));

        boolean canMark;
        String resolvedClassName;
        if (user.getRole() == Role.PRINCIPAL) {
            resolvedClassName = className;
            canMark = false;
            model.addAttribute("allClassNames", attendanceService.distinctClassNames());
        } else {
            Teacher teacher = attendanceService.requireTeacher(user);
            resolvedClassName = teacher.getAssignedClassName();
            canMark = teacher.isClassTeacher();
            model.addAttribute("teacher", teacher);
        }
        model.addAttribute("className", resolvedClassName);
        model.addAttribute("canMark", canMark);

        if (resolvedClassName == null || resolvedClassName.isBlank()) {
            return "teacher/attendance";
        }

        List<Student> roster = attendanceService.rosterFor(resolvedClassName);
        Map<String, AttendanceRecord> onDate = attendanceService.recordsByStudentFor(resolvedClassName, resolvedDate);

        AttendanceMarkForm form = new AttendanceMarkForm();
        for (Student student : roster) {
            AttendanceMarkForm.Entry entry = new AttendanceMarkForm.Entry();
            entry.setStudentId(student.getStudentId());
            entry.setFullName(student.getUser().getFullName());
            AttendanceRecord existing = onDate.get(student.getStudentId());
            entry.setStatus(existing != null ? existing.getStatus().name() : "PRESENT");
            form.getEntries().add(entry);
        }

        Map<String, String> statusByStudent = new java.util.HashMap<>();
        onDate.forEach((studentId, record) -> statusByStudent.put(studentId, record.getStatus().name()));
        boolean alreadyMarked = !onDate.isEmpty();

        model.addAttribute("roster", roster);
        model.addAttribute("form", form);
        model.addAttribute("alreadyMarked", alreadyMarked);
        // After submitting, the day is shown as a summary; "Edit" (or a day not marked yet) shows the tick boxes.
        model.addAttribute("editing", canMark && (!alreadyMarked || edit));
        model.addAttribute("statusByStudent", statusByStudent);
        if (alreadyMarked) model.addAttribute("daySummary", attendanceService.daySummary(resolvedClassName, resolvedDate));
        return "teacher/attendance";
    }

    @PostMapping("/mark")
    public String mark(@RequestParam String className, @RequestParam(required = false) String date,
                       @ModelAttribute("form") AttendanceMarkForm form,
                       Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        LocalDate resolvedDate = parseDateOrToday(date, LocalDate.now());
        try {
            attendanceService.markOrUpdate(className, resolvedDate, form, user);
            redirectAttributes.addFlashAttribute("success", "Attendance saved for " + className + " on " + resolvedDate + ".");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        redirectAttributes.addAttribute("className", className);
        redirectAttributes.addAttribute("date", resolvedDate.toString());
        return "redirect:/teacher/attendance";
    }

    @PostMapping("/delete-day")
    public String deleteDay(@RequestParam String className, @RequestParam String date,
                            Authentication authentication, RedirectAttributes redirectAttributes) {
        LocalDate resolvedDate = parseDateOrToday(date, LocalDate.now());
        try {
            int removed = attendanceService.deleteDay(className, resolvedDate, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Deleted the " + resolvedDate + " attendance record for " + className
                    + " (" + removed + " students). Please mark this day again.");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        redirectAttributes.addAttribute("className", className);
        redirectAttributes.addAttribute("date", resolvedDate.toString());
        return "redirect:/teacher/attendance";
    }

    private LocalDate parseDateOrToday(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    @GetMapping("/often-absent")
    public String oftenAbsent(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "attendance");

        String className = user.getRole() == Role.PRINCIPAL ? null : attendanceService.requireTeacher(user).getAssignedClassName();
        model.addAttribute("className", className);
        model.addAttribute("entries", attendanceService.oftenAbsent(className));
        return "teacher/attendance-often-absent";
    }
}
