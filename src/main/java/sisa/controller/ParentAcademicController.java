// Handles student results for parents
package sisa.controller;

import sisa.entity.Role;
import sisa.entity.Student;
import sisa.entity.User;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.MarksEntryService;
import sisa.report.ReportCardPdf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ParentAcademicController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final MarksEntryService marksEntryService;

    private final ReportCardPdf reportCardPdf;

    public ParentAcademicController(UserRepository userRepository, StudentRepository studentRepository,
                                    MarksEntryService marksEntryService, ReportCardPdf reportCardPdf) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.marksEntryService = marksEntryService;
        this.reportCardPdf = reportCardPdf;
    }

    @GetMapping("/parent/child-results/{studentId}/pdf")
    public ResponseEntity<byte[]> childReportCardPdf(@PathVariable String studentId, @RequestParam(required = false) Long termId,
                                                     Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        boolean ownChild = user.getRole() == Role.PARENT && studentRepository.findByParent_UserId(user.getUserId()).stream()
                .anyMatch(s -> s.getStudentId().equals(studentId));
        if (!ownChild) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only download your own child's report card.");
        }
        MarksEntryService.ReportCard card = marksEntryService.reportCardFor(studentId, termId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ReportCardPdf.fileName(card) + "\"")
                .body(reportCardPdf.render(card));
    }

    @GetMapping("/parent/child-results")
    public String childResults(@RequestParam(required = false) Long termId, Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        if (user.getRole() != Role.PARENT) {
            model.addAttribute("notice", "Only Parent accounts have children linked here.");
            return "parent/child-results";
        }
        List<Student> children = studentRepository.findByParent_UserId(user.getUserId());
        List<ChildResults> childData = children.stream()
                .map(child -> new ChildResults(child, marksEntryService.reportCardFor(child.getStudentId(), termId),
                        marksEntryService.termAverageTrend(child.getStudentId())))
                .toList();
        model.addAttribute("children", childData);
        model.addAttribute("terms", marksEntryService.allTerms());
        var selected = marksEntryService.termOrCurrent(termId);
        model.addAttribute("selectedTermId", selected != null ? selected.getId() : null);
        return "parent/child-results";
    }

    public record ChildResults(Student student, MarksEntryService.ReportCard reportCard,
                               List<MarksEntryService.TermAverage> trend) {}
}
