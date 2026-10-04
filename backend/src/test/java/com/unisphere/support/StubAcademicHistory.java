package com.unisphere.support;

import com.unisphere.registration.AcademicHistory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Lets integration tests declare which courses a student has passed, for prerequisite checks. */
public class StubAcademicHistory implements AcademicHistory {

    private final Map<Long, Set<Long>> passed = new ConcurrentHashMap<>();

    public void pass(Long studentId, Long... courseIds) {
        passed.computeIfAbsent(studentId, k -> new HashSet<>()).addAll(Set.of(courseIds));
    }

    public void clear() {
        passed.clear();
    }

    @Override
    public Set<Long> passedCourseIds(Long studentId) {
        return Set.copyOf(passed.getOrDefault(studentId, Set.of()));
    }
}
