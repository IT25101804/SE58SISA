package sisa.service;

import sisa.entity.AttendanceRecord;
import sisa.entity.User;
import sisa.event.AttendanceMarkedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AttendanceEventPublisher {

    private final ApplicationEventPublisher publisher;

    public AttendanceEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishAttendanceMarked(List<AttendanceRecord> absentOrLateRecords, User markedBy) {
        if (absentOrLateRecords.isEmpty()) return;
        publisher.publishEvent(new AttendanceMarkedEvent(this, absentOrLateRecords, markedBy));
    }
}
