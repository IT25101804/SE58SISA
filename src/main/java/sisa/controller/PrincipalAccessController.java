package sisa.controller;

import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.AuditLogRepository;
import sisa.repository.UserRepository;
import sisa.service.AccountAdminService;
import sisa.service.AccountApprovalService;
import sisa.service.AccountDeletionService;
import sisa.service.dto.CreateAccountRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/principal")
public class PrincipalAccessController {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccountApprovalService accountApprovalService;
    private final AccountAdminService accountAdminService;
    private final AccountDeletionService accountDeletionService;

    public PrincipalAccessController(UserRepository userRepository,
                                     AuditLogRepository auditLogRepository,
                                     AccountApprovalService accountApprovalService,
                                     AccountAdminService accountAdminService,
                                     AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.accountApprovalService = accountApprovalService;
        this.accountAdminService = accountAdminService;
        this.accountDeletionService = accountDeletionService;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    @GetMapping("/accounts")
    public String accounts(@RequestParam(required = false) String role,
                           @RequestParam(required = false) String q,
                           Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");

        var accounts = (role == null || role.isBlank())
                ? accountAdminService.listAll()
                : userRepository.findByRole(Role.valueOf(role.toUpperCase()));

        // Search by ID, name, username or email (case-insensitive), on top of the role filter.
        String search = q == null ? "" : q.trim().toLowerCase();
        if (!search.isEmpty()) {
            accounts = accounts.stream()
                    .filter(a -> contains(a.getUserId(), search) || contains(a.getFullName(), search)
                            || contains(a.getUsername(), search) || contains(a.getEmail(), search))
                    .toList();
        }

        model.addAttribute("accounts", accounts);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("selectedRole", role);
        model.addAttribute("roles", Role.values());
        return "principal/accounts";
    }

    @PostMapping("/accounts/{userId}/approve")
    public String approve(@PathVariable String userId, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        try {
            accountApprovalService.approve(userId, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Account " + userId + " approved.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/principal/accounts";
    }

    @PostMapping("/accounts/{userId}/reject")
    public String reject(@PathVariable String userId,
                         @RequestParam(required = false) String reason,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            accountApprovalService.reject(userId, currentUser(authentication), reason);
            redirectAttributes.addFlashAttribute("success", "Account " + userId + " rejected.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/principal/accounts";
    }

    @PostMapping("/accounts/{userId}/disable")
    public String disable(@PathVariable String userId, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        try {
            accountAdminService.disableAccount(userId, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Account " + userId + " disabled.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/principal/accounts";
    }

    @PostMapping("/accounts/{userId}/enable")
    public String enable(@PathVariable String userId, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            accountAdminService.enableAccount(userId, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Account " + userId + " re-enabled.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/principal/accounts";
    }

    @PostMapping("/accounts/{userId}/delete")
    public String delete(@PathVariable String userId, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            accountDeletionService.deleteAccount(userId, currentUser(authentication));
            redirectAttributes.addFlashAttribute("success", "Account " + userId + " permanently deleted.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/principal/accounts";
    }

    @GetMapping("/accounts/new-registrar")
    public String newRegistrarForm(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        model.addAttribute("request", new CreateAccountRequest());
        return "principal/new-registrar";
    }

    @PostMapping("/accounts/new-registrar")
    public String createRegistrar(@ModelAttribute("request") CreateAccountRequest request,
                                  Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        try {
            User created = accountAdminService.createRegistrar(request, currentUser(authentication));
            model.addAttribute("createdId", created.getUserId());
            model.addAttribute("request", new CreateAccountRequest());
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("request", request);
        }
        return "principal/new-registrar";
    }

    @GetMapping("/audit-log")
    public String auditLog(Authentication authentication, Model model) {
        model.addAttribute("user", currentUser(authentication));
        model.addAttribute("activeItem", "access");
        model.addAttribute("entries", auditLogRepository.findTop50ByOrderByTimestampDesc());
        return "principal/audit-log";
    }
}