package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "teachers")
@Getter
@Setter
@NoArgsConstructor
public class Teacher {

    @Id
    @Column(length = 20)
    private String teacherId;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    private User user;

    private String subjectSpecialty;
    private String joiningYear;

    private boolean classTeacher = false;
    private String assignedClassName;

    private Double monthlySalary;
}
