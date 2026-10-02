package sisa.service;

import sisa.entity.AccountStatus;
import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.UserRepository;
import sisa.service.dto.CreateAccountRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountAdminService {

    private final UserRepository userRepository;
    private final UserAccountFactory userAccountFactory;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AccountAdminService(UserRepository userRepository,
                               UserAccountFactory userAccountFactory,
                               PasswordEncoder passwordEncoder,
                               AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userAccountFactory = userAccountFactory;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public User createRegistrar(CreateAccountRequest req, User createdBy) {
        User registrar = userAccountFactory.createFor(Role.REGISTRAR, req);
        auditLogService.log(registrar.getUserId(), createdBy.getUserId(), "CREATE_REGISTRAR",
                createdBy.getFullName() + " created Registrar account " + registrar.getUserId());
        return registrar;
    }

    public User createTeacher(CreateAccountRequest req, User createdBy) {
        User teacher = userAccountFactory.createFor(Role.TEACHER, req);
        auditLogService.log(teacher.getUserId(), createdBy.getUserId(), "CREATE_TEACHER",
                createdBy.getFullName() + " created Teacher account " + teacher.getUserId());
        return teacher;
    }

    public User createParent(CreateAccountRequest req, User createdBy) {
        User parent = userAccountFactory.createFor(Role.PARENT, req);
        auditLogService.log(parent.getUserId(), createdBy.getUserId(), "CREATE_PARENT",
                createdBy.getFullName() + " created Parent account " + parent.getUserId());
        return parent;
    }

    public void disableAccount(String userId, User actingUser) {
        User user = requireNotPrincipal(userId);
        user.setStatus(AccountStatus.DISABLED);
        userRepository.save(user);
        auditLogService.log(userId, actingUser.getUserId(), "DISABLE",
                actingUser.getFullName() + " disabled account " + userId);
    }

    public void enableAccount(String userId, User actingUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No such account: " + userId));
        user.setStatus(AccountStatus.APPROVED);
        userRepository.save(user);
        auditLogService.log(userId, actingUser.getUserId(), "ENABLE",
                actingUser.getFullName() + " re-enabled account " + userId);
    }

    public void resetPassword(String userId, String newRawPassword, User actingUser) {
        User user = requireNotPrincipal(userId);
        user.setPassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
        auditLogService.log(userId, actingUser.getUserId(), "PASSWORD_RESET",
                actingUser.getFullName() + " reset the password for " + userId);
    }

    public List<User> listAll() {
        return userRepository.findAll();
    }

    private User requireNotPrincipal(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No such account: " + userId));
        if (user.getRole() == Role.PRINCIPAL || !user.isDeletable()) {
            throw new IllegalStateException("The permanent Principal account cannot be modified this way.");
        }
        return user;
    }
}