package com.unisphere.academic;

import com.unisphere.academic.dto.AcademicDtos.CourseDetail;
import com.unisphere.academic.dto.AcademicDtos.CourseRequest;
import com.unisphere.academic.dto.AcademicDtos.CourseSummary;
import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.common.persistence.Specs;
import com.unisphere.common.web.PageResponse;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final String ENTITY = "Course";

    private final CourseRepository courses;
    private final DepartmentService departments;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<CourseSummary> search(String q, Long departmentId, Integer level, Boolean active, Pageable pageable) {
        Page<Course> page = courses.findAll(Specs.allOf(
                CourseSpecifications.matchesQuery(q),
                CourseSpecifications.inDepartment(departmentId),
                CourseSpecifications.atLevel(level),
                CourseSpecifications.isActive(active)), pageable);
        Map<Long, Long> prerequisiteCounts = page.isEmpty()
                ? Map.of()
                : DepartmentService.toMap(courses.countPrerequisites(page.map(Course::getId).getContent()));
        return PageResponse.of(page, c -> CourseSummary.from(c, prerequisiteCounts.getOrDefault(c.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public CourseDetail get(Long id) {
        return CourseDetail.from(loadDetailed(id));
    }

    @Transactional
    public CourseDetail create(CourseRequest r) {
        validate(r, null);
        Course c = new Course(departments.load(r.departmentId()), r.code(), r.title(), r.description(), r.credits(), r.level());
        if (Boolean.FALSE.equals(r.active())) {
            c.update(c.getDepartment(), r.code(), r.title(), r.description(), r.credits(), r.level(), false);
        }
        courses.save(c);
        audit.record(AuditAction.CREATED, ENTITY, c.getId(), Map.of("code", c.getCode()));
        return CourseDetail.from(c);
    }

    @Transactional
    public CourseDetail update(Long id, CourseRequest r) {
        Course c = loadDetailed(id);
        validate(r, id);
        c.update(departments.load(r.departmentId()), r.code(), r.title(), r.description(), r.credits(), r.level(),
                r.active() == null || r.active());
        audit.record(AuditAction.UPDATED, ENTITY, id, Map.of("code", c.getCode(), "active", c.isActive()));
        return CourseDetail.from(c);
    }

    /**
     * Replaces the prerequisite set. Rejects unknown courses, self-references and any change that would
     * create a cycle (e.g. CS301 → CS201 → CS301), reporting the cycle by course code.
     */
    @Transactional
    public CourseDetail replacePrerequisites(Long id, List<Long> prerequisiteIds) {
        Course course = loadDetailed(id);
        Set<Long> ids = new LinkedHashSet<>(prerequisiteIds);
        if (ids.contains(id)) {
            throw ApiException.validation("prerequisiteIds", "A course cannot be its own prerequisite.");
        }
        List<Course> prerequisites = courses.findAllById(ids);
        if (prerequisites.size() != ids.size()) {
            throw ApiException.validation("prerequisiteIds", "One or more prerequisite courses do not exist.");
        }

        List<long[]> edges = courses.findAllPrerequisiteEdges().stream()
                .map(row -> new long[] {((Number) row[0]).longValue(), ((Number) row[1]).longValue()})
                .toList();
        new PrerequisiteGraph(edges).findCycleIfReplaced(id, ids).ifPresent(cycle -> {
            Map<Long, String> codes = new HashMap<>(prerequisites.stream()
                    .collect(Collectors.toMap(Course::getId, Course::getCode)));
            codes.put(id, course.getCode());
            courses.findAllById(cycle).forEach(c -> codes.putIfAbsent(c.getId(), c.getCode()));
            String path = cycle.stream().map(codes::get).collect(Collectors.joining(" → "));
            throw new ApiException(ErrorCode.PREREQUISITE_CYCLE,
                    "This would create a prerequisite cycle: %s. A course cannot (indirectly) require itself.".formatted(path))
                    .with("cycle", cycle);
        });

        course.replacePrerequisites(prerequisites);
        audit.record(AuditAction.PREREQUISITES_CHANGED, ENTITY, id, Map.of(
                "prerequisites", prerequisites.stream().map(Course::getCode).sorted().toList()));
        return CourseDetail.from(course);
    }

    public Course load(Long id) {
        return courses.findById(id).orElseThrow(() -> ApiException.notFound(ENTITY, id));
    }

    public Map<Long, Course> loadAll(Set<Long> ids) {
        return courses.findAllById(ids).stream().collect(Collectors.toMap(Course::getId, Function.identity()));
    }

    private Course loadDetailed(Long id) {
        return courses.findDetailedById(id).orElseThrow(() -> ApiException.notFound(ENTITY, id));
    }

    private void validate(CourseRequest r, Long excludeId) {
        if (r.level() % 100 != 0) {
            throw ApiException.validation("level", "must be a multiple of 100 (100, 200, … 900)");
        }
        if (courses.codeTaken(r.code().trim(), excludeId)) {
            throw ApiException.duplicate("Course code", r.code().trim().toUpperCase());
        }
    }
}
