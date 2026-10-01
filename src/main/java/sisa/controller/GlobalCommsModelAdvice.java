package sisa.controller;

import sisa.entity.User;
import sisa.repository.MessageRepository;
import sisa.repository.NotificationRepository;
import sisa.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts the current user's unread count (notifications + messages) into every page's
 * model, so layout/topbar.html's bell badge (business rule 5) works everywhere without
 * every controller in every module having to add it themselves.
 */
@ControllerAdvice
public class GlobalCommsModelAdvice {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final MessageRepository messageRepository;

    public GlobalCommsModelAdvice(UserRepository userRepository, NotificationRepository notificationRepository,
                                  MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.messageRepository = messageRepository;
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return 0;
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) return 0;
        long unreadNotifications = notificationRepository.countByRecipientUserIdAndReadFalseAndSentAtIsNotNull(user.getUserId());
        long unreadMessages = messageRepository.countByToUserIdAndReadFalse(user.getUserId());
        return unreadNotifications + unreadMessages;
    }
}
