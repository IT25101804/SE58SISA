package sisa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on Spring's @Scheduled support, for NotificationSchedulerService's poller. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
