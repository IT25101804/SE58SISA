package sisa.controller;

import sisa.entity.SavedReport;
import sisa.entity.User;
import sisa.report.ReportType;
import sisa.repository.UserRepository;
import sisa.service.SavedReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping({"/principal/reports/saved", "/registrar/reports/saved"})
public class SavedReportController {

    private final UserRepository userRepository;
    private final SavedReportService savedReportService;

    public SavedReportController(UserRepository userRepository, SavedReportService savedReportService) {
        this.userRepository = userRepository;
        this.savedReportService = savedReportService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    private static String reportsBase(HttpServletRequest request) {
        return request.getRequestURI().contains("/principal/") ? "/principal/reports" : "/registrar/reports";
    }
    // Build redirect URL with report filters
    private static String redirectTo(String base, ReportType type, String from, String to, String className) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath(base);
        if (type != null) uri.queryParam("type", type);
        if (from != null && !from.isBlank()) uri.queryParam("from", from);
        if (to != null && !to.isBlank()) uri.queryParam("to", to);
        if (className != null && !className.isBlank()) uri.queryParam("className", className);
        return "redirect:" + uri.encode().build().toUriString();
    }

    @PostMapping
    public String create(@RequestParam String name, @RequestParam ReportType type,
                         @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                         @RequestParam(required = false) String className,
                         Authentication authentication, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            SavedReport saved = savedReportService.create(name, type, from, to, className, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Saved report \"" + saved.getName() + "\". Click Open to view it.");
        } catch (ResponseStatusException rse) {
            redirectAttributes.addFlashAttribute("error", rse.getReason());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + reportsBase(request);
    }

    @GetMapping("/{id}")
    public String open(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        SavedReport saved = savedReportService.getOwnedOrThrow(id, currentUser(authentication));
        return redirectTo(reportsBase(request), saved.getReportType(),
                saved.getFromDate() == null ? null : saved.getFromDate().toString(),
                saved.getToDate() == null ? null : saved.getToDate().toString(),
                saved.getClassName());
    }
//update the report details
    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @RequestParam String name, @RequestParam ReportType type,
                         @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                         @RequestParam(required = false) String className,
                         Authentication authentication, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            SavedReport saved = savedReportService.update(id, name, type, from, to, className, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Updated saved report \"" + saved.getName() + "\".");
        } catch (ResponseStatusException rse) {
            redirectAttributes.addFlashAttribute("error", rse.getReason());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + reportsBase(request);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            String name = savedReportService.delete(id, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Deleted saved report \"" + name + "\".");
        } catch (ResponseStatusException rse) {
            redirectAttributes.addFlashAttribute("error", rse.getReason());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + reportsBase(request);
    }
}
