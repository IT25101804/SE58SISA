package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Teacher ID format: T2600001. */
@Entity
@Table(name = "teachers")
@Getter
@Setter
@NoArgsConstructor
public class Teacher {

    @Id
    @Column(length = 20)
    private String teacherId; // T2600001

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    private User user;

    private String subjectSpecialty;
    private String joiningYear;

    /** true = Class Teacher (marks daily attendance for one class), false = Subject Teacher only. */
    private boolean classTeacher = false;
    private String assignedClassName;

    /** Administration & Reporting Management's "staff-pay reports" (System Functions doc, Principal). Nullable — not every seeded/demo teacher has one set. */
    private Double monthlySalary;
}
