package com.unisphere.registration;

/**
 * Published when a registration is submitted, approved or rejected. It carries what listeners need, so they
 * never have to reload a registration that may have changed again since. Listeners run after the commit.
 *
 * @param reason the registrar's reason when {@code status} is REJECTED, otherwise null
 */
public record RegistrationStatusChangedEvent(Long studentUserId, String semesterName, RegistrationStatus status, String reason) {
}
