package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "behaviour_notes")
@Getter
@Setter
@NoArgsConstructor
public class BehaviourNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", referencedColumnName = "studentId", nullable = false)
    private Student student;

    @Column(nullable = false, length = 20)
    private String authorUserId;

    @Column(nullable = false, length = 100)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BehaviourCategory category = BehaviourCategory.NEUTRAL;

    @Column(nullable = false, length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
