package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Learning material / homework a Teacher posts for their class+subject (report FR-10, business rule 3). */
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

    /** Optional link/file reference for attached material — a plain URL/text field, no upload storage yet. */
    private String materialUrl;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Business rule 4: late submissions are allowed by default — this is the explicit opt-out. */
    @Column(nullable = false)
    private boolean submissionsClosed = false;
}
