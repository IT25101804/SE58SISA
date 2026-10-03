package sisa.controller;

import sisa.entity.Assignment;
import sisa.entity.Teacher;
import sisa.entity.TimetableSlot;
import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.AssignmentService;
import sisa.service.AttendanceService;
import sisa.service.TimetableService;
import sisa.service.dto.AssignmentForm;
import sisa.service.dto.GradeForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Controller
public class TeacherAssignmentController {

    private final UserRepository userRepository;
    private final AttendanceService attendanceService;
    private final TimetableService timetableService;
    private final AssignmentService assignmentService;

    public TeacherAssignmentController(UserRepository userRepository, AttendanceService attendanceService,
                                       TimetableService timetableService, AssignmentService assignmentService) {
        this.userRepository = userRepository;
        this.attendanceService = attendanceService;
        this.timetableService = timetableService;
        this.assignmentService = assignmentService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    private List<String> myClassNames(Teacher teacher) {
        TreeSet<String> names = timetableService.slotsForTeacher(teacher.getTeacherId()).stream()
                .map(TimetableSlot::getClassName)
                .collect(Collectors.toCollection(TreeSet::new));
        if (teacher.getAssignedClassName() != null && !teacher.getAssignedClassName().isBlank()) {
            names.add(teacher.getAssignedClassName());
        }
        return new java.util.ArrayList<>(names);
    }

    @GetMapping("/teacher/timetable")

    public String myTimetable(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");
        model.addAttribute("periods", PrincipalTimetableController.periodRange());
        model.addAttribute("days", TimetableService.SCHOOL_DAYS);

        Teacher teacher = attendanceService.requireTeacher(user);
        model.addAttribute("teacher", teacher);
        model.addAttribute("grid", timetableService.asGrid(timetableService.slotsForTeacher(teacher.getTeacherId())));
        return "teacher/timetable";
    }

    @GetMapping("/teacher/assignments")
    public String list(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");

        Teacher teacher = attendanceService.requireTeacher(user);
        model.addAttribute("assignments", assignmentService.listForTeacher(teacher.getTeacherId()));
        return "teacher/assignments";
    }

    @GetMapping("/teacher/assignments/new")
    public String newForm(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");
        AssignmentForm form = new AssignmentForm();
        model.addAttribute("form", form);
        List<String> classNames = List.of();
        try {
            Teacher teacher = attendanceService.requireTeacher(user);
            classNames = myClassNames(teacher);
            form.setSubject(teacher.getSubjectSpecialty());
        } catch (RuntimeException ignored) {

        }
        model.addAttribute("classNames", classNames);
        return "teacher/assignment-new";
    }

    @PostMapping("/teacher/assignments/new")
    public String create(@ModelAttribute("form") AssignmentForm form, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");
        Teacher teacher = null;
        try {
            teacher = attendanceService.requireTeacher(user);
            if (teacher.getSubjectSpecialty() == null || teacher.getSubjectSpecialty().isBlank()) {
                throw new IllegalArgumentException("You don't have a subject assigned yet — ask the Registrar to set your subject specialty.");
            }
            form.setSubject(teacher.getSubjectSpecialty());
            Assignment created = assignmentService.create(form, teacher);
            return "redirect:/teacher/assignments/" + created.getId();
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("classNames", teacher != null ? myClassNames(teacher) : List.of());
            return "teacher/assignment-new";
        }
    }

    @GetMapping("/teacher/assignments/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");
        Teacher teacher = attendanceService.requireTeacher(user);
        Assignment assignment = assignmentService.getOwned(id, teacher);

        AssignmentForm form = new AssignmentForm();
        form.setClassName(assignment.getClassName());
        form.setSubject(assignment.getSubject());
        form.setTitle(assignment.getTitle());
        form.setDescription(assignment.getDescription());
        form.setMaterialUrl(assignment.getMaterialUrl());
        form.setDueDate(assignment.getDueDate().toString());
        model.addAttribute("form", form);
        model.addAttribute("editId", id);
        model.addAttribute("classNames", classOptions(teacher, assignment.getClassName()));
        return "teacher/assignment-new";
    }

    @PostMapping("/teacher/assignments/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("form") AssignmentForm form,
                         Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        Teacher teacher = attendanceService.requireTeacher(user);
        Assignment assignment = assignmentService.getOwned(id, teacher);
        try {
            if (!classOptions(teacher, assignment.getClassName()).contains(form.getClassName())) {
                throw new IllegalArgumentException("Choose one of your classes.");
            }
            assignmentService.update(id, form, teacher);
            redirectAttributes.addFlashAttribute("success", "Assignment \"" + form.getTitle() + "\" updated.");
            return "redirect:/teacher/assignments";
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            model.addAttribute("user", user);
            model.addAttribute("activeItem", "timetable");
            model.addAttribute("error", ex.getMessage());
            form.setSubject(assignment.getSubject());
            model.addAttribute("form", form);
            model.addAttribute("editId", id);
            model.addAttribute("classNames", classOptions(teacher, assignment.getClassName()));
            return "teacher/assignment-new";
        }
    }

    @PostMapping("/teacher/assignments/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Teacher teacher = attendanceService.requireTeacher(currentUser(authentication));
        Assignment assignment = assignmentService.getOwned(id, teacher);
        assignmentService.delete(id, teacher);
        redirectAttributes.addFlashAttribute("success", "Assignment \"" + assignment.getTitle() + "\" deleted.");
        return "redirect:/teacher/assignments";
    }

    private List<String> classOptions(Teacher teacher, String currentClassName) {
        List<String> names = myClassNames(teacher);
        if (currentClassName != null && !names.contains(currentClassName)) {
            names.add(0, currentClassName);
        }
        return names;
    }

    @GetMapping("/teacher/assignments/{id}")
    public String detail(@PathVariable Long id, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "timetable");

        Assignment assignment = assignmentService.getOrThrow(id);
        model.addAttribute("assignment", assignment);
        model.addAttribute("submissions", assignmentService.submissionsFor(id));
        model.addAttribute("gradeForm", new GradeForm());
        return "teacher/assignment-detail";
    }

    @PostMapping("/teacher/assignments/{id}/grade")
    public String grade(@PathVariable Long id, @ModelAttribute("gradeForm") GradeForm form,
                        Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            Teacher teacher = attendanceService.requireTeacher(user);
            assignmentService.grade(id, form.getSubmissionId(), form.getGrade(), form.getFeedback(), teacher);
            redirectAttributes.addFlashAttribute("success", "Grade saved.");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/teacher/assignments/" + id;
    }

    @PostMapping("/teacher/assignments/{id}/close")
    public String close(@PathVariable Long id, @RequestParam boolean closed,
                        Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            Teacher teacher = attendanceService.requireTeacher(user);
            assignmentService.setSubmissionsClosed(id, closed, teacher);
            redirectAttributes.addFlashAttribute("success", closed ? "Submissions closed." : "Submissions reopened.");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/teacher/assignments/" + id;
    }
}
