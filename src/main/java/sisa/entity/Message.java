package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One direct message in a simple 1:1 thread (report FR-11, business rule 3): a
 * "thread" is just every Message between two userIds, in either direction, ordered
 * by sentAt — see MessagingService.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String fromUserId;

    @Column(nullable = false, length = 20)
    private String toUserId;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    // Column renamed to "is_read" — "read" is a reserved keyword in SQL Server's T-SQL
    // dialect and breaks CREATE TABLE there; the Java field/getter/setter name is
    // unchanged, so no other code needed to change.
    @Column(name = "is_read", nullable = false)
    private boolean read = false;
}
