package sisa.controller;

import sisa.entity.User;
import sisa.report.ReportType;
import sisa.report.ReportingAggregationService;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.SavedReportService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * The Principal's full report picker (report FR-12, business rule 1: full access) —
 * every ReportType, any date range, an on-screen table + chart, and Export as
 * PDF/Excel buttons pointing at ReportExportController. Access is already scoped to
 * PRINCIPAL by SecurityConfig's /principal/** rule.
 */
@Controller
@RequestMapping("/principal/reports")
public class PrincipalReportingController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ReportingAggregationService reportingAggregationService;
    private final SavedReportService savedReportService;

    public PrincipalReportingController(UserRepository userRepository, StudentRepository studentRepository,
                                        ReportingAggregationService reportingAggregationService,
                                        SavedReportService savedReportService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.reportingAggregationService = reportingAggregationService;
        this.savedReportService = savedReportService;
    }

    @GetMapping
    public String reports(@RequestParam(required = false, defaultValue = "ENROLMENT") ReportType type,
                          @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          @RequestParam(required = false) String className,
                          Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "reports");
        model.addAttribute("reportTypes", ReportType.values());
        model.addAttribute("selectedType", type);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("className", className);
        model.addAttribute("allClassNames", studentRepository.distinctClassNames());
        model.addAttribute("savedReports", savedReportService.listFor(user));
        model.addAttribute("savedBase", "/principal/reports/saved");

        ReportingAggregationService.Report report = reportingAggregationService.build(
                type, parseDate(from), parseDate(to), className, true);
        model.addAttribute("report", report.data());
        model.addAttribute("chart", report.chart());
        return "principal/reports";
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDate.parse(raw);
    }
}
