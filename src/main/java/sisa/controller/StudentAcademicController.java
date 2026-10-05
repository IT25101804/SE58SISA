// Handles student academic results
package sisa.controller;

import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.MarksEntryService;
import sisa.report.ReportCardPdf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StudentAcademicController {

    private final UserRepository userRepository;
    private final MarksEntryService marksEntryService;
    private final ReportCardPdf reportCardPdf;

    public StudentAcademicController(UserRepository userRepository, MarksEntryService marksEntryService,
                                     ReportCardPdf reportCardPdf) {
        this.userRepository = userRepository;
        this.marksEntryService = marksEntryService;
        this.reportCardPdf = reportCardPdf;
    }

    @GetMapping("/student/results/pdf")
    public ResponseEntity<byte[]> resultsPdf(@RequestParam(required = false) Long termId, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Student accounts have a report card here.");
        }
        MarksEntryService.ReportCard card = marksEntryService.reportCardFor(user.getUserId(), termId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ReportCardPdf.fileName(card) + "\"")
                .body(reportCardPdf.render(card));
    }

    @GetMapping("/student/results")
    public String results(@RequestParam(required = false) Long termId, Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "academic");

        if (user.getRole() != Role.STUDENT) {
            model.addAttribute("notice", "Only Student accounts have results here.");
            return "student/results";
        }
        MarksEntryService.ReportCard reportCard = marksEntryService.reportCardFor(user.getUserId(), termId);
        model.addAttribute("reportCard", reportCard);
        model.addAttribute("terms", marksEntryService.allTerms());
        model.addAttribute("selectedTermId", reportCard.term() != null ? reportCard.term().getId() : null);
        return "student/results";
    }
}
