package com.unisphere.offering;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long>, JpaSpecificationExecutor<CourseOffering> {

    @Override
    @EntityGraph(attributePaths = {"course", "course.department", "semester", "lecturer", "lecturer.user"})
    Page<CourseOffering> findAll(Specification<CourseOffering> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"course", "course.department", "semester", "lecturer", "lecturer.user", "sessions"})
    @Query("select o from CourseOffering o where o.id = :id")
    Optional<CourseOffering> findDetailedById(@Param("id") Long id);

    @Query("""
            select count(o) > 0 from CourseOffering o
            where o.course.id = :courseId and o.semester.id = :semesterId and upper(o.section) = upper(:section)
              and (:excludeId is null or o.id <> :excludeId)""")
    boolean sectionTaken(@Param("courseId") Long courseId, @Param("semesterId") Long semesterId,
                         @Param("section") String section, @Param("excludeId") Long excludeId);

    /** Every session in the semester except those of {@code excludeId}, for clash detection. */
    @Query("""
            select s from OfferingSession s join fetch s.offering o join fetch o.course
            left join fetch o.lecturer
            where o.semester.id = :semesterId and (:excludeId is null or o.id <> :excludeId)""")
    List<OfferingSession> findSessionsInSemester(@Param("semesterId") Long semesterId, @Param("excludeId") Long excludeId);

    boolean existsBySemesterId(Long semesterId);

    boolean existsByCourseId(Long courseId);

    boolean existsByLecturerId(Long lecturerId);
}
