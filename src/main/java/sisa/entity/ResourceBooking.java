package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    private String decidedBy;
    private LocalDateTime decidedAt;
}
