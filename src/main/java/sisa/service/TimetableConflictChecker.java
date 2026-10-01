package sisa.service;

import sisa.entity.TimetableSlot;
import sisa.repository.TimetableSlotRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.Optional;

/**
 * Double-booking checks for a timetable slot. The teacher/period conflict is
 * business rule 2's own check; the room/period conflict is delegated to
 * BookingConflictChecker so a TimetableSlot's room and Module 8's ad-hoc
 * ResourceBooking share one source of truth for room availability (report section 7,
 * item 10, business rule 4).
 */
@Service
public class TimetableConflictChecker {

    private final TimetableSlotRepository timetableSlotRepository;
    private final BookingConflictChecker bookingConflictChecker;

    public TimetableConflictChecker(TimetableSlotRepository timetableSlotRepository,
                                     BookingConflictChecker bookingConflictChecker) {
        this.timetableSlotRepository = timetableSlotRepository;
        this.bookingConflictChecker = bookingConflictChecker;
    }

    /**
     * Returns the other slot already booking this teacher for this day+period, if any,
     * excluding the slot currently being edited (pass null when creating a new one).
     */
    public Optional<TimetableSlot> conflictFor(String teacherId, DayOfWeek dayOfWeek, int periodNumber, Long excludingSlotId) {
        return timetableSlotRepository.findByTeacher_TeacherIdAndDayOfWeekAndPeriodNumber(teacherId, dayOfWeek, periodNumber)
                .filter(slot -> excludingSlotId == null || !slot.getId().equals(excludingSlotId));
    }

    /**
     * Returns a description of whatever already occupies this room at this day+period
     * (another timetable slot, or an ad-hoc ResourceBooking whose date falls on that
     * day-of-week), if any. Room is optional on a slot, so a null roomResourceId is not a conflict.
     */
    public Optional<BookingConflictChecker.BookingConflict> roomConflictFor(Long roomResourceId, DayOfWeek dayOfWeek, int periodNumber, Long excludingSlotId) {
        return bookingConflictChecker.conflictForTimetableSlot(roomResourceId, dayOfWeek, periodNumber, excludingSlotId);
    }
}
