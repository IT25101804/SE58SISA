package sisa.observer;

import sisa.event.AttendanceMarkedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AdminReportObserver {

    private static final Logger log = LoggerFactory.getLogger(AdminReportObserver.class);

    @EventListener
    public void onAttendanceMarked(AttendanceMarkedEvent event) {
        log.info("AdminReportObserver stub: {} absent/late record(s) submitted by {} (real aggregation lands in Module 7).",
                event.getAbsentOrLateRecords().size(), event.getMarkedBy().getUserId());
    }
}
