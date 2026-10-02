package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Student Information Management module. Student ID format: S2600001. */
@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student {

    @Id
    @Column(length = 20)
    private String studentId; // S2600001

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    private User user;

    private String admissionYear;
    private String className;   // e.g. Grade 10 - A
    private String guardianName;
    private String guardianContact;

    @ManyToOne
    @JoinColumn(name = "parent_id", referencedColumnName = "userId")
    private User parent; // auto-linked Parent account, per report section 6.3

    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private String emergencyContact;
    private LocalDate enrollmentDate;

    /** ACTIVE / TRANSFERRED / ARCHIVED. Archiving is a soft-delete — the row is kept for school records. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentStatus status = StudentStatus.ACTIVE;

    /** Free-text reason attached to the last transfer/archive action, shown on the profile. */
    private String statusNote;
}
