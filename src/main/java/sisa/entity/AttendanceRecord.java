package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One student's attendance for one day (report FR-05/FR-06). At most one row per
 * (student, date) — "one submission per class per day" (business rule 1) — but the
 * Class Teacher may edit it later the same day (business rule 4), which is tracked
 * via lastEditedBy/lastEditedAt plus an AuditLogEntry.
 */
@Entity
@Table(name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_student_date", columnNames = {"student_id", "attendance_date"}))
@Getter
@Setter
@NoArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", referencedColumnName = "studentId", nullable = false)
    private Student student;

    /** Snapshot of the student's class at the time this was marked (classes can change later via transfer). */
    @Column(nullable = false, length = 60)
    private String className;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceStatus status;

    /** Teacher userId who first submitted this record. */
    @Column(nullable = false, length = 20)
    private String markedBy;

    @Column(nullable = false)
    private LocalDateTime markedAt;

    /** Set only when the record is edited after its initial submission (business rule 4). */
    private String lastEditedBy;
    private LocalDateTime lastEditedAt;
}
