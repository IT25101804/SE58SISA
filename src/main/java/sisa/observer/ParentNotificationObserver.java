package sisa.observer;

import sisa.entity.AttendanceRecord;
import sisa.entity.Notification;
import sisa.entity.User;
import sisa.event.AttendanceMarkedEvent;
import sisa.repository.NotificationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ParentNotificationObserver {

    private final NotificationRepository notificationRepository;

    public ParentNotificationObserver(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void onAttendanceMarked(AttendanceMarkedEvent event) {
        for (AttendanceRecord record : event.getAbsentOrLateRecords()) {
            User parent = record.getStudent().getParent();
            if (parent == null) continue;

            String studentName = record.getStudent().getUser().getFullName();
            String message = studentName + " was marked " + record.getStatus() + " on " + record.getAttendanceDate() + ".";
            notificationRepository.save(new Notification(parent.getUserId(), message));
        }
    }
}
