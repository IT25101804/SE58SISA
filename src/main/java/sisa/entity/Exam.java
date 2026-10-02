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
    private String className;

    @Column(nullable = false, length = 80)
    private String subject;

    @Column(nullable = false)
    private String examName;

    @Column(nullable = false)
    private LocalDate examDate;

    @Column(nullable = false)
    private double maxMarks;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_teacher_id", referencedColumnName = "teacherId", nullable = false)
    private Teacher createdBy;
}
