package sisa.repository;

import sisa.entity.BookingStatus;
import sisa.entity.ResourceBooking;
import sisa.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ResourceBookingRepository extends JpaRepository<ResourceBooking, Long> {

    List<ResourceBooking> findByResource_IdAndBookingDateAndPeriodNumberAndStatusNot(
            Long resourceId, LocalDate bookingDate, int periodNumber, BookingStatus excludedStatus);

    List<ResourceBooking> findByResource_IdAndPeriodNumberAndStatusNot(Long resourceId, int periodNumber, BookingStatus excludedStatus);

    List<ResourceBooking> findByBookingDateAndStatusNot(LocalDate bookingDate, BookingStatus excludedStatus);

    List<ResourceBooking> findByStatusOrderByRequestedAtAsc(BookingStatus status);

    long countByBookingDate(LocalDate bookingDate);

    long countByBookingDateAndStatusAndResource_Type(LocalDate bookingDate, BookingStatus status, ResourceType type);

    long countByResource_Id(Long resourceId);
}
