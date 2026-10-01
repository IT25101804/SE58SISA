package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Student Information Management module (System Functions doc, Teacher: "Record
 * behaviour/discipline notes"; Principal/Registrar: "View discipline records").
 * Only the student's own Class Teacher may add a note — enforced in
 * TeacherAcademicController#addBehaviourNote — but any staff role that can already
 * open the student's record (Principal, Registrar, or that same Class Teacher) may
 * read the list.
 */
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
