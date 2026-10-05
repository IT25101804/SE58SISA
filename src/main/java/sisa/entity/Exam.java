package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String className; // Class assigned to the exam

    @Column(nullable = false, length = 80)
    private String subject; // Subject of the exam

    @Column(nullable = false)
    private String examName;

    @Column(nullable = false)
    private LocalDate examDate;

    @Column(nullable = false)
    private double maxMarks; // Maximum marks for the exam

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_teacher_id", referencedColumnName = "teacherId", nullable = false)
    private Teacher createdBy; // Teacher who created the exam

    @ManyToOne
    @JoinColumn(name = "term_id")
    private AcademicTerm term; // Academic term of the exam
}
