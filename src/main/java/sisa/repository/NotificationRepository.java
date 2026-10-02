package sisa.repository;

import sisa.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);
    long countByRecipientUserIdAndReadFalse(String recipientUserId);

    /** Visible inbox items only — sentAt null means "not due yet" (business rule 1). */
    List<Notification> findByRecipientUserIdAndSentAtIsNotNullOrderBySentAtDesc(String recipientUserId);
    long countByRecipientUserIdAndReadFalseAndSentAtIsNotNull(String recipientUserId);
    List<Notification> findTop5ByRecipientUserIdAndSentAtIsNotNullOrderBySentAtDesc(String recipientUserId);

    /** For the scheduler poller. */
    List<Notification> findByScheduledForIsNotNullAndSentAtIsNull();

    /** For the Principal/Registrar/Teacher "sent" logs, grouped in the service by broadcastId. */
    List<Notification> findBySenderUserIdAndBroadcastIdIsNotNullOrderByCreatedAtDesc(String senderUserId);
    List<Notification> findByBroadcastIdIsNotNullOrderByCreatedAtDesc();
    List<Notification> findByBroadcastId(String broadcastId);

    /** Cancelling a scheduled (not-yet-sent) broadcast — see AnnouncementService.deleteScheduledBroadcast. */
    void deleteByBroadcastId(String broadcastId);

    /** Clean-up when an account is permanently deleted — see AccountDeletionService. */
    void deleteByRecipientUserId(String recipientUserId);
}
