package com.unisphere.academic;

import com.unisphere.common.persistence.Specs;
import org.springframework.data.jpa.domain.Specification;

final class CourseSpecifications {

    private CourseSpecifications() {
    }

    static Specification<Course> matchesQuery(String q) {
        String pattern = Specs.containsPattern(q);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern, '\\'),
                cb.like(cb.lower(root.get("title")), pattern, '\\'));
    }

    static Specification<Course> inDepartment(Long departmentId) {
        return departmentId == null ? null : (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
    }

    static Specification<Course> atLevel(Integer level) {
        return level == null ? null : (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    static Specification<Course> isActive(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
