package com.unisphere.notification;

import com.unisphere.common.config.UniSphereProperties;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes notifications older than the retention period ({@code NOTIFICATION_RETENTION}, 180 days by default), so
 * inboxes and the table do not grow forever. Old reminders are safe to delete: their dedupe keys name past
 * semesters and reminder windows, which never come round again.
 */
@Service
@RequiredArgsConstructor
public class NotificationCleanup {

    private final NotificationRepository notifications;
    private final UniSphereProperties properties;
    private final Clock clock;

    /** @return how many notifications were deleted */
    @Transactional
    public int deleteExpired() {
        Instant cutoff = clock.instant().minus(properties.notifications().retention());
        return notifications.deleteCreatedBefore(cutoff);
    }
}
