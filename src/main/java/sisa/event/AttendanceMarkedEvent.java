package sisa.event;

import sisa.entity.AttendanceRecord;
import sisa.entity.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Published once per attendance submission/edit (report section 4.5's Observer pattern).
 * Carries only the ABSENT/LATE records from that submission — subscribers (see
 * {@code sisa.observer}) decide what to do about them without the marking
 * code needing to know they exist. Spring's {@code ApplicationEventPublisher} +
 * {@code @EventListener} is the idiomatic Observer implementation here: listeners run
 * synchronously in the same request/transaction unless one opts into {@code @Async}.
 */
public class AttendanceMarkedEvent extends ApplicationEvent {

    private final List<AttendanceRecord> absentOrLateRecords;
    private final User markedBy;

    public AttendanceMarkedEvent(Object source, List<AttendanceRecord> absentOrLateRecords, User markedBy) {
        super(source);
        this.absentOrLateRecords = absentOrLateRecords;
        this.markedBy = markedBy;
    }

    public List<AttendanceRecord> getAbsentOrLateRecords() {
        return absentOrLateRecords;
    }

    public User getMarkedBy() {
        return markedBy;
    }
}
