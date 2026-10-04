package com.unisphere.registration;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Until results exist no student has passed any course, so courses with prerequisites cannot be registered
 * — exactly what happens to a first-year student in a real system. Phase 4 replaces this class with an
 * implementation backed by finalized results.
 */
@Component
class NoResultsAcademicHistory implements AcademicHistory {

    @Override
    public Set<Long> passedCourseIds(Long studentId) {
        return Set.of();
    }
}
