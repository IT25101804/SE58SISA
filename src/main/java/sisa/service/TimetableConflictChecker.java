package sisa.service;

import sisa.entity.TimetableSlot;
import sisa.repository.TimetableSlotRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.Optional;

@Service
public class TimetableConflictChecker {  //This class Act as Facade

    // Under TimetableConflictChecker there are two components
    private final TimetableSlotRepository timetableSlotRepository;
    private final BookingConflictChecker bookingConflictChecker;

    public TimetableConflictChecker(TimetableSlotRepository timetableSlotRepository,
                                     BookingConflictChecker bookingConflictChecker) {
        this.timetableSlotRepository = timetableSlotRepository;
        this.bookingConflictChecker = bookingConflictChecker;
    }

    public Optional<TimetableSlot> conflictFor(String teacherId, DayOfWeek dayOfWeek, int periodNumber, Long excludingSlotId) {
        return timetableSlotRepository.findByTeacher_TeacherIdAndDayOfWeekAndPeriodNumber(teacherId, dayOfWeek, periodNumber)
                .filter(slot -> excludingSlotId == null || !slot.getId().equals(excludingSlotId));
    }

    public Optional<BookingConflictChecker.BookingConflict> roomConflictFor(Long roomResourceId, DayOfWeek dayOfWeek, int periodNumber, Long excludingSlotId) {
        return bookingConflictChecker.conflictForTimetableSlot(roomResourceId, dayOfWeek, periodNumber, excludingSlotId);
    }
}
