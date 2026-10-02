package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @Column(length = 20)
    private String studentId;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    private User user;

    private String admissionYear;
    private String className;
    private String guardianName;
    private String guardianContact;

    @ManyToOne
    @JoinColumn(name = "parent_id", referencedColumnName = "userId")
    private User parent;

    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private String emergencyContact;
    private LocalDate enrollmentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentStatus status = StudentStatus.ACTIVE;

    private String statusNote;
}
