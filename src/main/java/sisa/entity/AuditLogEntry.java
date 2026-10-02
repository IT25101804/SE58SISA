package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String userId;

    @Column(nullable = false, length = 20)
    private String performedByUserId;

    @Column(nullable = false, length = 40)
    private String action;

    private String detail;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public AuditLogEntry(String userId, String performedByUserId, String action, String detail) {
        this.userId = userId;
        this.performedByUserId = performedByUserId;
        this.action = action;
        this.detail = detail;
    }
}