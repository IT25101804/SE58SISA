package sisa.observer;

import sisa.event.AttendanceMarkedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer #2 (report section 4.5) — a stub. Module 7 (Administration & Reporting)
 * will subscribe to the same AttendanceMarkedEvent to roll absences into Principal-facing
 * reports; wiring it here now (even as a no-op beyond logging) proves the Observer
 * pattern decouples "attendance was marked" from every downstream concern, including
 * ones that don't exist yet.
 */
@Component
public class AdminReportObserver {

    private static final Logger log = LoggerFactory.getLogger(AdminReportObserver.class);

    @EventListener
    public void onAttendanceMarked(AttendanceMarkedEvent event) {
        log.info("AdminReportObserver stub: {} absent/late record(s) submitted by {} (real aggregation lands in Module 7).",
                event.getAbsentOrLateRecords().size(), event.getMarkedBy().getUserId());
    }
}
