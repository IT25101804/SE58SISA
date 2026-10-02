package sisa.service;

import sisa.entity.BookingStatus;
import sisa.entity.ResourceBooking;
import sisa.entity.TimetableSlot;
import sisa.repository.ResourceBookingRepository;
import sisa.repository.TimetableSlotRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class BookingConflictChecker {

    private final ResourceBookingRepository resourceBookingRepository;
    private final TimetableSlotRepository timetableSlotRepository;

    public BookingConflictChecker(ResourceBookingRepository resourceBookingRepository,
                                   TimetableSlotRepository timetableSlotRepository) {
        this.resourceBookingRepository = resourceBookingRepository;
        this.timetableSlotRepository = timetableSlotRepository;
    }

    public sealed interface BookingConflict permits BookingConflict.WithBooking, BookingConflict.WithTimetableSlot {

        String describe();

        record WithBooking(ResourceBooking booking) implements BookingConflict {
            public String describe() {
                return "Already booked by " + booking.getBookedByUserId() + " for \"" + booking.getPurpose()
                        + "\" on " + booking.getBookingDate() + " period " + booking.getPeriodNumber()
                        + " (status: " + booking.getStatus() + ") — choose a different resource, date or period.";
            }
        }

        record WithTimetableSlot(TimetableSlot slot) implements BookingConflict {
            public String describe() {
                return "Already used for " + slot.getSubject() + " (" + slot.getClassName() + ") every "
                        + slot.getDayOfWeek() + " period " + slot.getPeriodNumber()
                        + " per the timetable — choose a different resource or period.";
            }
        }
    }

    public Optional<BookingConflict> conflictForBooking(Long resourceId, LocalDate bookingDate, int periodNumber, Long excludingBookingId) {
        List<ResourceBooking> clashing = resourceBookingRepository
                .findByResource_IdAndBookingDateAndPeriodNumberAndStatusNot(resourceId, bookingDate, periodNumber, BookingStatus.REJECTED);
        for (ResourceBooking booking : clashing) {
            if (excludingBookingId == null || !booking.getId().equals(excludingBookingId)) {
                return Optional.of(new BookingConflict.WithBooking(booking));
            }
        }

        DayOfWeek dayOfWeek = bookingDate.getDayOfWeek();
        List<TimetableSlot> timetableClash = timetableSlotRepository
                .findByRoom_IdAndDayOfWeekAndPeriodNumber(resourceId, dayOfWeek, periodNumber);
        if (!timetableClash.isEmpty()) {
            return Optional.of(new BookingConflict.WithTimetableSlot(timetableClash.get(0)));
        }
        return Optional.empty();
    }

    public Optional<BookingConflict> conflictForTimetableSlot(Long roomResourceId, DayOfWeek dayOfWeek, int periodNumber, Long excludingSlotId) {
        if (roomResourceId == null) return Optional.empty();

        List<TimetableSlot> slotClash = timetableSlotRepository
                .findByRoom_IdAndDayOfWeekAndPeriodNumber(roomResourceId, dayOfWeek, periodNumber);
        for (TimetableSlot slot : slotClash) {
            if (excludingSlotId == null || !slot.getId().equals(excludingSlotId)) {
                return Optional.of(new BookingConflict.WithTimetableSlot(slot));
            }
        }

        List<ResourceBooking> bookingClash = resourceBookingRepository
                .findByResource_IdAndPeriodNumberAndStatusNot(roomResourceId, periodNumber, BookingStatus.REJECTED);
        return bookingClash.stream()
                .filter(booking -> booking.getBookingDate().getDayOfWeek() == dayOfWeek)
                .findFirst()
                .map(BookingConflict.WithBooking::new);
    }
}
