package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The one notification record for the whole app (report section 4.5's Observer
 * pattern, business rule 4's NotificationCenter shape) — started small in Module 3
 * for parent attendance alerts, extended here into the full Communication &
 * Notification Management model rather than duplicated. Every module should create
 * these through AnnouncementService (fan-out) or the simple constructor below
 * (single system-generated notice, as Module 3's observer already does).
 *
 * Visibility rule (business rule 1 + TC "scheduled announcement"): a row is visible
 * to its recipient once {@code sentAt} is set. Immediate notices get it at creation
 * time; scheduled ones get it later, from NotificationSchedulerService's poller.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String recipientUserId;

    /** Null for system-generated notices (e.g. Module 3's attendance observer) that have no human sender. */
    @Column(length = 20)
    private String senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category = NotificationCategory.ALERT;

    private String subject;

    @Column(nullable = false, length = 1000)
    private String body;

    /** Null for one-off system notices that were never a broadcast (e.g. attendance alerts). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotificationScope targetScope;

    /**
     * Shared by every recipient row fanned out from the same AnnouncementService.create()
     * call, so the Principal's "message log" can show one row per broadcast rather than
     * one per recipient. Null for single-recipient system notices.
     */
    private String broadcastId;

    // Column renamed to "is_read" — "read" is a reserved keyword in SQL Server's T-SQL
    // dialect and breaks CREATE TABLE there; the Java field/getter/setter name is
    // unchanged, so no other code needed to change.
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    /** Null = send immediately (visible right away); set = deliver later (business rule 1). */
    private LocalDateTime scheduledFor;

    /** Set at creation for immediate notices, or by the scheduler poller once scheduledFor is due. Null = not yet visible. */
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Convenience constructor for a single, immediate, system-generated notice (Module 3's usage). */
    public Notification(String recipientUserId, String body) {
        this.recipientUserId = recipientUserId;
        this.body = body;
        this.category = NotificationCategory.ALERT;
        this.sentAt = LocalDateTime.now();
    }
}
