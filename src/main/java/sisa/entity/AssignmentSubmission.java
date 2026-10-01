package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A Student's answer to one Assignment (report FR-10, business rules 4 & 5). One row per (assignment, student) — resubmitting replaces it. */
@Entity
@Table(name = "assignment_submissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_submission_assignment_student", columnNames = {"assignment_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AssignmentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", referencedColumnName = "studentId", nullable = false)
    private Student student;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(length = 2000)
    private String fileUrlOrText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubmissionStatus status;

    /** A free-form mark ("18/20", "A+", "85") — no fixed grading scale exists yet (that's Module 5). */
    private String grade;
    private String feedback;
    private String gradedBy;
    private LocalDateTime gradedAt;
}
