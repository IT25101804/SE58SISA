package sisa.repository;

import sisa.entity.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long> {
    List<TimetableSlot> findByClassNameOrderByPeriodNumberAsc(String className);
    List<TimetableSlot> findByTeacher_TeacherIdOrderByPeriodNumberAsc(String teacherId);
    Optional<TimetableSlot> findByTeacher_TeacherIdAndDayOfWeekAndPeriodNumber(String teacherId, DayOfWeek dayOfWeek, int periodNumber);
    Optional<TimetableSlot> findByClassNameAndDayOfWeekAndPeriodNumber(String className, DayOfWeek dayOfWeek, int periodNumber);

    /** Used by Module 5 (Academic Management) to check a Subject Teacher is actually assigned to this class+subject. */
    boolean existsByTeacher_TeacherIdAndClassNameAndSubject(String teacherId, String className, String subject);

    /** Module 8 (Resources & Facilities) room double-booking check — see BookingConflictChecker. */
    List<TimetableSlot> findByRoom_IdAndDayOfWeekAndPeriodNumber(Long roomResourceId, DayOfWeek dayOfWeek, int periodNumber);

    /** Guards BookingService#deleteResource — a room still assigned on the timetable can't be deleted. */
    boolean existsByRoom_Id(Long roomResourceId);
    boolean existsByTeacher_TeacherId(String teacherId);
}
