package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    private String grade;
    private String feedback;
    private String gradedBy;
    private LocalDateTime gradedAt;
}
