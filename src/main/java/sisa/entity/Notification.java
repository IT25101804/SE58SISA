package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    @Column(length = 20)
    private String senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category = NotificationCategory.ALERT;

    private String subject;

    @Column(nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotificationScope targetScope;

    private String broadcastId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    private LocalDateTime scheduledFor;

    private LocalDateTime sentAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification(String recipientUserId, String body) {
        this.recipientUserId = recipientUserId;
        this.body = body;
        this.category = NotificationCategory.ALERT;
        this.sentAt = LocalDateTime.now();
    }
}
