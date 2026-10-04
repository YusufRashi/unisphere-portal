package com.unisphere.offering;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Takes a seat only if one is free; returns 0 when the offering is full. Atomic in a single statement. */
    @Modifying
    @Query(value = "update course_offerings set enrolled_count = enrolled_count + 1 where id = :id and enrolled_count < capacity",
            nativeQuery = true)
    int takeSeat(@Param("id") Long id);

    @Modifying
    @Query(value = "update course_offerings set enrolled_count = enrolled_count - 1 where id = :id and enrolled_count > 0",
            nativeQuery = true)
    int releaseSeat(@Param("id") Long id);

    @Query(value = "select enrolled_count from course_offerings where id = :id", nativeQuery = true)
    int currentEnrolledCount(@Param("id") Long id);

    /** Row-locks the offering so results transitions (save, submit, return, finalize) run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CourseOffering o where o.id = :id")
    Optional<CourseOffering> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"course", "course.department", "semester", "lecturer", "lecturer.user"})
    List<CourseOffering> findByLecturerIdAndSemesterIdOrderByCourseCodeAscSectionAsc(Long lecturerId, Long semesterId);

    /** Whether any student of the offering has marks recorded (native: results live in a later feature). */
    @Query(value = """
            select exists (select 1 from results r join registration_items i on i.id = r.registration_item_id
                           where i.offering_id = :id and (r.cat_mark is not null or r.exam_mark is not null))""",
            nativeQuery = true)
    boolean hasMarks(@Param("id") Long id);

    boolean existsBySemesterId(Long semesterId);

    boolean existsByCourseId(Long courseId);

    boolean existsByLecturerId(Long lecturerId);
}
