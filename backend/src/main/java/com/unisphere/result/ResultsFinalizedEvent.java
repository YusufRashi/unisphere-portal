package com.unisphere.result;

/** Published when the registrar finalizes an offering's results. Listeners run after the commit. */
public record ResultsFinalizedEvent(Long offeringId, String courseCode, String courseTitle, String semesterName) {
}
