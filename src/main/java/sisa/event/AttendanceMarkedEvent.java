package sisa.event;

import sisa.entity.AttendanceRecord;
import sisa.entity.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

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
