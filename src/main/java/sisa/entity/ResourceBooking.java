package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One request to use a Resource for a single date + period (report FR-13, business
 * rule 3: no two of these may overlap for the same resource/date/period — see
 * BookingConflictChecker). periodNumber reuses TimetableService's 1..MAX_PERIODS
 * numbering so the same slot vocabulary describes both a recurring TimetableSlot and
 * an ad-hoc booking (report section 7, item 10).
 */
@Entity
@Table(name = "resource_bookings")
@Getter
@Setter
@NoArgsConstructor
public class ResourceBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    /** Any staff userId (Teacher/Registrar/Principal) — not a single-role FK, like Message.fromUserId. */
    @Column(nullable = false, length = 20)
    private String bookedByUserId;

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private int periodNumber;

    @Column(nullable = false, length = 200)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingStatus status = BookingStatus.REQUESTED;

    @Column(nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    /** Set once the Principal (or auto-approval) decides — see BookingService. */
    private String decidedBy;
    private LocalDateTime decidedAt;
}
