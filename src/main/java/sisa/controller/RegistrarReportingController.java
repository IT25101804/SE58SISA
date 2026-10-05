package sisa.controller;

import sisa.entity.Role;
import sisa.entity.User;
import sisa.report.ReportType;
import sisa.report.ReportingAggregationService;
import sisa.repository.StudentRepository;
import sisa.repository.UserRepository;
import sisa.service.SavedReportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

@Controller
@RequestMapping("/registrar/reports")
public class RegistrarReportingController {

    private static final Set<ReportType> ALLOWED_TYPES =
            EnumSet.of(ReportType.ENROLMENT, ReportType.TRANSFER, ReportType.CLASS_LIST, ReportType.STAFF);

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ReportingAggregationService reportingAggregationService;
    private final SavedReportService savedReportService;

    public RegistrarReportingController(UserRepository userRepository, StudentRepository studentRepository,
                                        ReportingAggregationService reportingAggregationService,
                                        SavedReportService savedReportService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.reportingAggregationService = reportingAggregationService;
        this.savedReportService = savedReportService;
    }

    @GetMapping
    public String reports(@RequestParam(required = false) ReportType type,
                          @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          @RequestParam(required = false) String className,
                          Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (type != null && user.getRole() == Role.REGISTRAR && !ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The Registrar can only view Enrolment, Transfer, Class List and Staff reports.");
        }
        //Get logging user
        model.addAttribute("user", user);
        model.addAttribute("activeItem", "reports");
        model.addAttribute("reportTypes", ALLOWED_TYPES);
        model.addAttribute("selectedType", type);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("className", className);
        model.addAttribute("allClassNames", studentRepository.distinctClassNames());
        model.addAttribute("savedReports", savedReportService.listFor(user));
        model.addAttribute("savedBase", "/registrar/reports/saved");

        if (type != null) {
            ReportingAggregationService.Report report = reportingAggregationService.build(
                    type, parseDate(from), parseDate(to), className, false);
            model.addAttribute("report", report.data());
            model.addAttribute("chart", report.chart());
        }
        return "registrar/reports";
    }
// Convert data String to local date
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDate.parse(raw);
    }
}
