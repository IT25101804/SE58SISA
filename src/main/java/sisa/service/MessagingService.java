package sisa.service;

import sisa.entity.Message;
import sisa.entity.Role;
import sisa.entity.User;
import sisa.repository.MessageRepository;
import sisa.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class MessagingService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public MessagingService(MessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Message send(User from, String toUserId, String body) {
        User to = userRepository.findById(toUserId)
                .orElseThrow(() -> new IllegalArgumentException("No such user: " + toUserId));
        requireValidPair(from.getRole(), to.getRole());

        Message message = new Message();
        message.setFromUserId(from.getUserId());
        message.setToUserId(toUserId);
        message.setBody(body);
        return messageRepository.save(message);
    }

    private void requireValidPair(Role fromRole, Role toRole) {
        boolean valid = switch (fromRole) {
            case TEACHER -> toRole == Role.STUDENT || toRole == Role.PARENT || toRole == Role.TEACHER || toRole == Role.PRINCIPAL;
            case STUDENT -> toRole == Role.TEACHER;
            case PARENT -> toRole == Role.TEACHER;
            case PRINCIPAL -> toRole == Role.TEACHER;
            default -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A " + fromRole + " cannot message a " + toRole + ".");
        }
    }

    public List<Message> threadWith(String userId, String partnerId) {
        return messageRepository.threadBetween(userId, partnerId);
    }

    @Transactional
    public void markThreadRead(String userId, String partnerId) {
        for (Message m : messageRepository.threadBetween(userId, partnerId)) {
            if (m.getToUserId().equals(userId) && !m.isRead()) {
                m.setRead(true);
                messageRepository.save(m);
            }
        }
    }

    public record ThreadSummary(String partnerUserId, String partnerName, Message lastMessage, long unreadCount) {}

    public List<ThreadSummary> threadsFor(String userId) {
        List<Message> all = messageRepository.findAllInvolving(userId);
        Map<String, List<Message>> byPartner = new LinkedHashMap<>();
        for (Message m : all) {
            String partnerId = m.getFromUserId().equals(userId) ? m.getToUserId() : m.getFromUserId();
            byPartner.computeIfAbsent(partnerId, k -> new ArrayList<>()).add(m);
        }
        List<ThreadSummary> summaries = new ArrayList<>();
        for (var entry : byPartner.entrySet()) {
            String partnerId = entry.getKey();
            Message last = entry.getValue().get(0);
            long unread = entry.getValue().stream().filter(m -> m.getToUserId().equals(userId) && !m.isRead()).count();
            User partner = userRepository.findById(partnerId).orElse(null);
            summaries.add(new ThreadSummary(partnerId, partner != null ? partner.getFullName() : partnerId, last, unread));
        }
        return summaries;
    }

    public long unreadMessageCount(String userId) {
        return messageRepository.countByToUserIdAndReadFalse(userId);
    }
}
