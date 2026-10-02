package sisa.controller;

import sisa.entity.Role;
import sisa.entity.User;
import sisa.report.*;
import sisa.report.ReportExportService;
import sisa.report.ReportExportStrategy;
import sisa.report.ReportType;
import sisa.report.ReportingAggregationService;
import sisa.repository.UserRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Streams a report as a file download (report FR-12, business rule 2's Strategy
 * pattern): pick a ReportExportStrategy by format, hand it the same ReportData the
 * on-screen table renders. Two endpoints, not one shared unprefixed route, so
 * SecurityConfig's existing /principal/** and /registrar/** role rules do the access
 * control (business rule 1) without any config change — the Registrar's endpoint adds
 * one more check narrowing to its partial report subset.
 */
@RestController
public class ReportExportController {

    private static final Set<ReportType> REGISTRAR_ALLOWED_TYPES =
            EnumSet.of(ReportType.ENROLMENT, ReportType.TRANSFER, ReportType.CLASS_LIST, ReportType.STAFF);

    private final UserRepository userRepository;
    private final ReportingAggregationService reportingAggregationService;
    private final ReportExportService reportExportService;

    public ReportExportController(UserRepository userRepository, ReportingAggregationService reportingAggregationService,
                                  ReportExportService reportExportService) {
        this.userRepository = userRepository;
        this.reportingAggregationService = reportingAggregationService;
        this.reportExportService = reportExportService;
    }

    @GetMapping("/principal/reports/export")
    public ResponseEntity<byte[]> exportForPrincipal(@RequestParam ReportType type, @RequestParam String format,
                                                      @RequestParam(required = false) String from,
                                                      @RequestParam(required = false) String to,
                                                      @RequestParam(required = false) String className,
                                                      Authentication authentication) {
        return export(type, format, from, to, className, true, authentication);
    }

    @GetMapping("/registrar/reports/export")
    public ResponseEntity<byte[]> exportForRegistrar(@RequestParam ReportType type, @RequestParam String format,
                                                      @RequestParam(required = false) String from,
                                                      @RequestParam(required = false) String to,
                                                      @RequestParam(required = false) String className,
                                                      Authentication authentication) {
        User user = currentUser(authentication);
        if (user.getRole() == Role.REGISTRAR && !REGISTRAR_ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The Registrar can only export Enrolment, Transfer, Class List and Staff reports.");
        }
        return export(type, format, from, to, className, false, authentication);
    }

    private ResponseEntity<byte[]> export(ReportType type, String format, String from, String to, String className,
                                          boolean includeStaffPerformance, Authentication authentication) {
        currentUser(authentication); // just confirms the session resolves to a real account
        ReportingAggregationService.Report report = reportingAggregationService.build(
                type, parseDate(from), parseDate(to), className, includeStaffPerformance);
        ReportExportStrategy strategy = reportExportService.strategyFor(format);
        byte[] bytes = strategy.export(report.data());

        String filename = type.name().toLowerCase() + "-report." + strategy.fileExtension();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(strategy.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDate.parse(raw);
    }
}
