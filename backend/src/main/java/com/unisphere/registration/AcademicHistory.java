package com.unisphere.registration;

import java.util.Set;

/**
 * Which courses a student has already passed — the input for prerequisite checks. Kept behind an interface
 * so registration does not depend on how results are stored; the results feature implements it from
 * finalized grades.
 */
public interface AcademicHistory {

    Set<Long> passedCourseIds(Long studentId);
}
