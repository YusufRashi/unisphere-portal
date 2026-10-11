package com.unisphere.semester;

import java.time.Instant;

/** Published when an administrator opens registration for a semester. Listeners run after the commit. */
public record RegistrationOpenedEvent(Long semesterId, String semesterName, Instant opensAt, Instant closesAt) {
}
