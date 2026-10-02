package sisa.service;

import sisa.entity.AccountStatus;
import sisa.entity.User;
import sisa.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountApprovalService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public AccountApprovalService(UserRepository userRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public List<User> listPending() {
        return userRepository.findByStatus(AccountStatus.PENDING);
    }

    public void approve(String userId, User decidedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No such account: " + userId));
        user.setStatus(AccountStatus.APPROVED);
        userRepository.save(user);
        auditLogService.log(userId, decidedBy.getUserId(), "APPROVE",
                decidedBy.getFullName() + " approved " + user.getRole() + " account " + userId);
    }

    public void reject(String userId, User decidedBy, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No such account: " + userId));
        user.setStatus(AccountStatus.REJECTED);
        userRepository.save(user);
        auditLogService.log(userId, decidedBy.getUserId(), "REJECT",
                decidedBy.getFullName() + " rejected " + user.getRole() + " account " + userId
                        + (reason == null || reason.isBlank() ? "" : " — reason: " + reason));
    }
}