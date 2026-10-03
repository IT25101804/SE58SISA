package sisa.controller;

import sisa.entity.BehaviourCategory;
import sisa.entity.BehaviourNote;
import sisa.entity.Exam;
import sisa.entity.Mark;
import sisa.entity.Student;
import sisa.entity.Teacher;
import sisa.entity.User;
import sisa.repository.BehaviourNoteRepository;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.AttendanceService;
import sisa.service.MarksEntryService;
import sisa.service.dto.ExamForm;
import sisa.service.dto.MarksEntryForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/teacher/academic")
public class TeacherAcademicController {

    private final UserRepository userRepository;
    private final MarksEntryService marksEntryService;
    private final AttendanceService attendanceService;
    private final BehaviourNoteRepository behaviourNoteRepository;
    private final StudentRepository studentRepository;

    public TeacherAcademicController(UserRepository userRepository, MarksEntryService marksEntryService,
                                     AttendanceService attendanceService, BehaviourNoteRepository behaviourNoteRepository,
                                     StudentRepository studentRepository) {
        this.userRepository = userRepository;
        this.marksEntryService = marksEntryService;
        this.attendanceService = attendanceService;
        this.behaviourNoteRepository = behaviourNoteRepository;
        this.studentRepository = studentRepository;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        Teacher teacher = marksEntryService.requireTeacher(user);
        model.addAttribute("teacher", teacher);
        var exams = marksEntryService.listForTeacher(teacher);
        var terms = marksEntryService.allTerms();
        java.util.Map<Long, String> examTerms = new java.util.HashMap<>();
        for (Exam e : exams) {
            var t = marksEntryService.termOf(e, terms);
            examTerms.put(e.getId(), t != null ? t.getName() : "—");
        }
        model.addAttribute("exams", exams);
        model.addAttribute("examTerms", examTerms);
        return "teacher/academic-exams";
    }

    @GetMapping("/new")
    public String newForm(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");
        ExamForm form = new ExamForm();
        var current = marksEntryService.currentTerm();
        if (current != null) form.setTermId(current.getId());
        model.addAttribute("form", form);
        model.addAttribute("terms", marksEntryService.allTerms());
        model.addAttribute("classOptions", marksEntryService.classSubjectOptionsFor(marksEntryService.requireTeacher(user)));
        return "teacher/academic-exam-new";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("form") ExamForm form, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");
        try {
            Exam created = marksEntryService.createExam(form, user);
            return "redirect:/teacher/academic/" + created.getId();
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex instanceof ResponseStatusException rse ? rse.getReason() : ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("terms", marksEntryService.allTerms());
            model.addAttribute("classOptions", marksEntryService.classSubjectOptionsFor(marksEntryService.requireTeacher(user)));
            return "teacher/academic-exam-new";
        }
    }

    @GetMapping("/{examId}")
    public String marksEntry(@PathVariable Long examId, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        Exam exam = marksEntryService.getOrThrow(examId);
        model.addAttribute("exam", exam);

        List<Student> roster = attendanceService.rosterFor(exam.getClassName());
        Map<String, Mark> marks = marksEntryService.marksByStudent(examId);

        MarksEntryForm form = new MarksEntryForm();
        for (Student student : roster) {
            MarksEntryForm.Entry entry = new MarksEntryForm.Entry();
            entry.setStudentId(student.getStudentId());
            entry.setFullName(student.getUser().getFullName());
            Mark existing = marks.get(student.getStudentId());
            entry.setMarksObtained(existing != null ? String.valueOf(existing.getMarksObtained()) : "");
            form.getEntries().add(entry);
        }
        model.addAttribute("roster", roster);
        model.addAttribute("marks", marks);
        model.addAttribute("form", form);
        return "teacher/academic-marks";
    }

    @PostMapping("/{examId}/marks")
    public String saveMarks(@PathVariable Long examId, @ModelAttribute("form") MarksEntryForm form,
                            Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            marksEntryService.enterMarks(examId, form, user);
            redirectAttributes.addFlashAttribute("success", "Marks saved.");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/teacher/academic/" + examId;
    }

    @PostMapping("/{examId}/delete")
    public String deleteExam(@PathVariable Long examId, Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            marksEntryService.deleteExam(examId, user);
            redirectAttributes.addFlashAttribute("success", "Exam deleted.");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/teacher/academic";
    }

    @GetMapping("/report-cards")
    public String reportCards(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        Teacher teacher = marksEntryService.requireTeacher(user);
        model.addAttribute("teacher", teacher);
        if (teacher.isClassTeacher() && teacher.getAssignedClassName() != null) {
            model.addAttribute("className", teacher.getAssignedClassName());
            model.addAttribute("roster", attendanceService.rosterFor(teacher.getAssignedClassName()));
        }
        return "teacher/academic-report-cards";
    }

    @GetMapping("/report-cards/{studentId}")
    public String reportCard(@PathVariable String studentId, @RequestParam(required = false) Long termId,
                             Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        Teacher teacher = marksEntryService.requireTeacher(user);
        MarksEntryService.ReportCard reportCard = marksEntryService.reportCardForAsClassTeacher(studentId, teacher.getAssignedClassName(), user, termId);
        model.addAttribute("reportCard", reportCard);
        model.addAttribute("terms", marksEntryService.allTerms());
        model.addAttribute("selectedTermId", reportCard.term() != null ? reportCard.term().getId() : null);
        model.addAttribute("behaviourNotes", behaviourNoteRepository.findByStudent_StudentIdOrderByCreatedAtDesc(studentId));
        model.addAttribute("behaviourCategories", BehaviourCategory.values());
        return "teacher/academic-report-card-view";
    }

    @PostMapping("/report-cards/{studentId}/notes")
    public String addBehaviourNote(@PathVariable String studentId, @RequestParam String note,
                                   @RequestParam(defaultValue = "NEUTRAL") BehaviourCategory category,
                                   Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        Teacher teacher = marksEntryService.requireTeacher(user);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("No such student: " + studentId));
        if (!teacher.isClassTeacher() || !student.getClassName().equals(teacher.getAssignedClassName())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only " + student.getClassName() + "'s Class Teacher can add a behaviour note here.");
        }
        if (note == null || note.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Note can't be empty.");
            return "redirect:/teacher/academic/report-cards/" + studentId;
        }
        BehaviourNote entry = new BehaviourNote();
        entry.setStudent(student);
        entry.setAuthorUserId(user.getUserId());
        entry.setAuthorName(user.getFullName());
        entry.setCategory(category);
        entry.setNote(note.trim());
        behaviourNoteRepository.save(entry);
        redirectAttributes.addFlashAttribute("success", "Behaviour note added.");
        return "redirect:/teacher/academic/report-cards/" + studentId;
    }
}
