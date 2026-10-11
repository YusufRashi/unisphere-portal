package com.unisphere.notification;

import com.unisphere.notification.NotificationWriter.Message;
import com.unisphere.registration.RegistrationStatusChangedEvent;
import com.unisphere.result.ResultsFinalizedEvent;
import com.unisphere.semester.RegistrationOpenedEvent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns domain events into notifications (NTF-4, REG-10, RES-6). Listeners run only after the business change
 * has committed, so a rolled-back action never notifies anyone, and in a transaction of their own, so a failure
 * here is logged and can never undo an approval or a finalization.
 */
@Component
class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationWriter writer;
    private final TransactionTemplate tx;

    NotificationEventListener(NotificationWriter writer, PlatformTransactionManager transactionManager) {
        this.writer = writer;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener
    void on(RegistrationOpenedEvent e) {
        Message message = new Message(NotificationType.REGISTRATION_OPENED,
                "Registration is open for " + e.semesterName(),
                "Course registration runs from %s until %s. Add your courses and submit before it closes."
                        .formatted(Formats.dateTime(e.opensAt()), Formats.dateTime(e.closesAt())),
                "/student/registration");
        // Re-opening registration (after closing it by mistake, say) does not notify everyone a second time.
        deliver(e, () -> writer.sendTo(Recipients.ACTIVE_STUDENTS, Map.of(), message, "REGISTRATION_OPENED:" + e.semesterId()));
    }

    @TransactionalEventListener
    void on(RegistrationStatusChangedEvent e) {
        Message message = switch (e.status()) {
            case SUBMITTED -> new Message(NotificationType.REGISTRATION_SUBMITTED, "Registration submitted",
                    "Your registration for %s has been submitted and is waiting for approval.".formatted(e.semesterName()),
                    "/student/registration");
            case APPROVED -> new Message(NotificationType.REGISTRATION_APPROVED, "Registration confirmed",
                    "Your registration for %s is confirmed. Your timetable is ready.".formatted(e.semesterName()),
                    "/student/timetable");
            case REJECTED -> new Message(NotificationType.REGISTRATION_REJECTED, "Registration not approved",
                    "Your registration for %s was not approved: %s. You can change your courses and submit again."
                            .formatted(e.semesterName(), e.reason()),
                    "/student/registration");
            case DRAFT -> null;
        };
        if (message != null) {
            deliver(e, () -> writer.send(e.studentUserId(), message, null));
        }
    }

    @TransactionalEventListener
    void on(ResultsFinalizedEvent e) {
        Message message = new Message(NotificationType.RESULTS_RELEASED, "Results released: " + e.courseCode(),
                "Your final result for %s %s (%s) is now available.".formatted(e.courseCode(), e.courseTitle(), e.semesterName()),
                "/student/results");
        deliver(e, () -> writer.sendTo(Recipients.CLASS_LIST, Map.of("offeringId", e.offeringId()), message,
                "RESULTS_RELEASED:" + e.offeringId()));
    }

    private void deliver(Object event, Runnable work) {
        try {
            tx.executeWithoutResult(status -> work.run());
        } catch (RuntimeException ex) {
            log.error("Could not create notifications for {}", event, ex);
        }
    }
}
