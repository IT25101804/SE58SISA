package sisa.observer;

import sisa.entity.AttendanceRecord;
import sisa.entity.Notification;
import sisa.entity.User;
import sisa.event.AttendanceMarkedEvent;
import sisa.repository.NotificationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer #1 (report section 4.5): as soon as attendance is submitted, tell each
 * absent/late student's linked parent. Runs synchronously within the same request that
 * marked attendance (business rule 3 / TC-05), so the Notification exists by the time
 * the HTTP response returns — no separate delivery worker needed for this to be true.
 * Real email/SMS delivery is Module 6's job; this just writes the in-app record.
 */
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
            if (parent == null) continue; // shouldn't happen post Module 2, but never block attendance on it

            String studentName = record.getStudent().getUser().getFullName();
            String message = studentName + " was marked " + record.getStatus() + " on " + record.getAttendanceDate() + ".";
            notificationRepository.save(new Notification(parent.getUserId(), message));
        }
    }
}
