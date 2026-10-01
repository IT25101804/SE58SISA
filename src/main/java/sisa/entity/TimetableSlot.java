package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;

/**
 * One class-subject-teacher-room booking for a single period on a single day
 * (report FR-09, section 6.2). Only the Principal may create/edit these
 * (business rule 1) — see TimetableConflictChecker for the teacher/period
 * double-booking check run before every save (business rule 2).
 */
@Entity
@Table(name = "timetable_slots",
        uniqueConstraints = @UniqueConstraint(name = "uk_timetable_class_day_period", columnNames = {"class_name", "day_of_week", "period_number"}))
@Getter
@Setter
@NoArgsConstructor
public class TimetableSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_name", nullable = false, length = 60)
    private String className;

    @Column(nullable = false, length = 80)
    private String subject;

    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id", referencedColumnName = "teacherId", nullable = false)
    private Teacher teacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    /**
     * Module 8 (School Resources & Facilities) integration (report section 7, item 10):
     * a real Resource reference instead of a free-text room name, checked for
     * double-booking through the same BookingConflictChecker a one-off ResourceBooking
     * uses — one source of truth for room availability (business rule 4). Nullable:
     * not every slot needs a fixed room booked through this module.
     */
    @ManyToOne
    @JoinColumn(name = "room_resource_id")
    private Resource room;

    /** Convenience accessor so existing "roomName" display templates need no changes. */
    public String getRoomName() {
        return room == null ? null : room.getName();
    }
}
