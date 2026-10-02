package sisa.config;

import sisa.repository.UserRepository;
import sisa.service.AuditLogService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class LoginAuditListener {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public LoginAuditListener(UserRepository userRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            auditLogService.log(user.getUserId(), user.getUserId(), "LOGIN", "Successful login");
        });
    }
}