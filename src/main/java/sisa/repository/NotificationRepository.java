package sisa.repository;

import sisa.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);
    long countByRecipientUserIdAndReadFalse(String recipientUserId);

    List<Notification> findByRecipientUserIdAndSentAtIsNotNullOrderBySentAtDesc(String recipientUserId);
    long countByRecipientUserIdAndReadFalseAndSentAtIsNotNull(String recipientUserId);
    List<Notification> findTop5ByRecipientUserIdAndSentAtIsNotNullOrderBySentAtDesc(String recipientUserId);

    List<Notification> findByScheduledForIsNotNullAndSentAtIsNull();

    List<Notification> findBySenderUserIdAndBroadcastIdIsNotNullOrderByCreatedAtDesc(String senderUserId);
    List<Notification> findByBroadcastIdIsNotNullOrderByCreatedAtDesc();
    List<Notification> findByBroadcastId(String broadcastId);

    void deleteByBroadcastId(String broadcastId);

    void deleteByRecipientUserId(String recipientUserId);
}
