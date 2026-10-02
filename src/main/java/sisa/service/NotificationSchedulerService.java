package sisa.service;

import sisa.entity.Notification;
import sisa.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The "poller" for scheduled announcements (business rule 1): every due, not-yet-sent
 * Notification gets sentAt set, which is what makes it visible to its recipient (see
 * Notification's class doc). {@code flipDueToSent()} is exposed separately from the
 * {@code @Scheduled} method so tests can trigger it deterministically instead of
 * waiting on a real timer.
 */
@Service
public class NotificationSchedulerService {

    private final NotificationRepository notificationRepository;

    public NotificationSchedulerService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(fixedRate = 60_000)
    public void poll() {
        flipDueToSent();
    }

    @Transactional
    public int flipDueToSent() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> due = notificationRepository.findByScheduledForIsNotNullAndSentAtIsNull();
        int flipped = 0;
        for (Notification notification : due) {
            if (!notification.getScheduledFor().isAfter(now)) {
                notification.setSentAt(now);
                notificationRepository.save(notification);
                flipped++;
            }
        }
        return flipped;
    }
}
