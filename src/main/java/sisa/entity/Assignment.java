package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id", referencedColumnName = "teacherId", nullable = false)
    private Teacher teacher;

    @Column(nullable = false, length = 60)
    private String className;

    @Column(nullable = false, length = 80)
    private String subject;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private String materialUrl;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean submissionsClosed = false;
}
