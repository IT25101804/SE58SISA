package sisa.repository;

import sisa.entity.BookingStatus;
import sisa.entity.ResourceBooking;
import sisa.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ResourceBookingRepository extends JpaRepository<ResourceBooking, Long> {

    /** Active (non-rejected) bookings for one resource/date/period — the double-booking check. */
    List<ResourceBooking> findByResource_IdAndBookingDateAndPeriodNumberAndStatusNot(
            Long resourceId, LocalDate bookingDate, int periodNumber, BookingStatus excludedStatus);

    /** Active bookings for a resource+period on any date — used to cross-check against a recurring TimetableSlot's day-of-week. */
    List<ResourceBooking> findByResource_IdAndPeriodNumberAndStatusNot(Long resourceId, int periodNumber, BookingStatus excludedStatus);

    /** Every active booking on a given date, for the availability grid (report FR-13). */
    List<ResourceBooking> findByBookingDateAndStatusNot(LocalDate bookingDate, BookingStatus excludedStatus);

    List<ResourceBooking> findByStatusOrderByRequestedAtAsc(BookingStatus status);

    /**
     * Generic usage-count hook for Module 7 (business rule 5) — a simple summary any
     * reporting service can call without depending on the rest of this module.
     */
    long countByBookingDate(LocalDate bookingDate);

    long countByBookingDateAndStatusAndResource_Type(LocalDate bookingDate, BookingStatus status, ResourceType type);

    /** Guards BookingService#deleteResource — any booking (past or present) still points at the resource_id FK. */
    long countByResource_Id(Long resourceId);
}
