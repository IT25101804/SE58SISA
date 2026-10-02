package sisa.service;

import sisa.entity.Role;
import sisa.entity.SavedReport;
import sisa.entity.User;
import sisa.report.ReportType;
import sisa.repository.SavedReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class SavedReportService {

    public static final Set<ReportType> REGISTRAR_TYPES =
            EnumSet.of(ReportType.ENROLMENT, ReportType.TRANSFER, ReportType.CLASS_LIST, ReportType.STAFF);

    private final SavedReportRepository savedReportRepository;
    private final AuditLogService auditLogService;

    public SavedReportService(SavedReportRepository savedReportRepository, AuditLogService auditLogService) {
        this.savedReportRepository = savedReportRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SavedReport create(String name, ReportType type, String from, String to, String className, User owner) {
        SavedReport report = new SavedReport();
        report.setOwnerUserId(owner.getUserId());
        applyFields(report, name, type, from, to, className, owner);
        savedReportRepository.save(report);
        auditLogService.log(owner.getUserId(), owner.getUserId(), "CREATE_SAVED_REPORT",
                owner.getFullName() + " saved report \"" + report.getName() + "\" (" + type + ")");
        return report;
    }

    public List<SavedReport> listFor(User owner) {
        return savedReportRepository.findByOwnerUserIdOrderByNameAsc(owner.getUserId());
    }

    public SavedReport getOwnedOrThrow(Long id, User owner) {
        SavedReport report = savedReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such saved report: " + id));
        if (!report.getOwnerUserId().equals(owner.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That saved report isn't yours.");
        }
        return report;
    }

    @Transactional
    public SavedReport update(Long id, String name, ReportType type, String from, String to, String className, User owner) {
        SavedReport report = getOwnedOrThrow(id, owner);
        applyFields(report, name, type, from, to, className, owner);
        report.setUpdatedAt(LocalDateTime.now());
        savedReportRepository.save(report);
        auditLogService.log(owner.getUserId(), owner.getUserId(), "UPDATE_SAVED_REPORT",
                owner.getFullName() + " updated saved report \"" + report.getName() + "\"");
        return report;
    }

    @Transactional
    public String delete(Long id, User owner) {
        SavedReport report = getOwnedOrThrow(id, owner);
        savedReportRepository.delete(report);
        auditLogService.log(owner.getUserId(), owner.getUserId(), "DELETE_SAVED_REPORT",
                owner.getFullName() + " deleted saved report \"" + report.getName() + "\"");
        return report.getName();
    }

    private void applyFields(SavedReport report, String name, ReportType type, String from, String to,
                             String className, User owner) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Give the saved report a name.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Choose a report type.");
        }
        if (owner.getRole() == Role.REGISTRAR && !REGISTRAR_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The Registrar can only save Enrolment, Transfer, Class List and Staff reports.");
        }
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("The From date must be on or before the To date.");
        }
        report.setName(name.trim());
        report.setReportType(type);
        report.setFromDate(fromDate);
        report.setToDate(toDate);
        report.setClassName(StringUtils.hasText(className) ? className.trim() : null);
    }

    private LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        return LocalDate.parse(raw);
    }
}
