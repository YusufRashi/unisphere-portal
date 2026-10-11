package com.unisphere.notification;

/** What a notification is about. Stored as text, so adding a type needs no migration. */
public enum NotificationType {
    REGISTRATION_OPENED,
    REGISTRATION_DEADLINE,
    REGISTRATION_SUBMITTED,
    REGISTRATION_APPROVED,
    REGISTRATION_REJECTED,
    RESULTS_RELEASED,
    FEE_REMINDER,
    ANNOUNCEMENT
}
