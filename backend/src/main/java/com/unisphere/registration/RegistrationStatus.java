package com.unisphere.registration;

/**
 * DRAFT → SUBMITTED → APPROVED, or DRAFT → APPROVED directly when the semester needs no approval.
 * SUBMITTED → REJECTED releases the seats; the student's next change turns it back into a DRAFT.
 */
public enum RegistrationStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED
}
