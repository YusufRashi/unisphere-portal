package com.unisphere.result;

import com.unisphere.registration.AcademicHistory;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Prerequisites are satisfied by a finalized, passing result (REG-4). Marks still in draft or awaiting the
 * registrar do not count, and a pass is never undone by a later attempt at the same course.
 */
@Component
@RequiredArgsConstructor
public class ResultsAcademicHistory implements AcademicHistory {

    private final CourseResultRepository results;

    @Override
    public Set<Long> passedCourseIds(Long studentId) {
        return Set.copyOf(results.findPassedCourseIds(studentId));
    }
}
