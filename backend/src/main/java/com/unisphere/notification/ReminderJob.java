package com.unisphere.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link ReminderService} on the configured schedule ({@code unisphere.notifications.reminder-cron}). */
@Component
class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final ReminderService reminders;

    ReminderJob(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "${unisphere.notifications.reminder-cron}", zone = "UTC")
    void run() {
        try {
            ReminderService.Result result = reminders.sendDueReminders();
            log.info("Reminders sent: {} registration deadline, {} fee balance", result.registrationDeadline(), result.feeBalance());
        } catch (RuntimeException ex) {
            log.error("Reminder job failed", ex);
        }
    }
}
