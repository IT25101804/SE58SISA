package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One student's marks for one Exam (report FR-07/FR-08). One row per (exam, student). */
@Entity
@Table(name = "marks", uniqueConstraints = @UniqueConstraint(name = "uk_marks_exam_student", columnNames = {"exam_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Mark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", referencedColumnName = "studentId", nullable = false)
    private Student student;

    @Column(nullable = false)
    private double marksObtained;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Grade grade;

    /** Teacher userId who entered/last edited this mark. */
    private String enteredBy;
    private LocalDateTime enteredAt;
}
