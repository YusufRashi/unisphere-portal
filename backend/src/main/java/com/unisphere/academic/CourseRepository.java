package com.unisphere.academic;

import java.util.Collection;
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

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

    @Override
    @EntityGraph(attributePaths = "department")
    Page<Course> findAll(Specification<Course> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "prerequisites"})
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findDetailedById(@Param("id") Long id);

    @Query("select count(c) > 0 from Course c where upper(c.code) = upper(:code) and (:excludeId is null or c.id <> :excludeId)")
    boolean codeTaken(@Param("code") String code, @Param("excludeId") Long excludeId);

    /** Every prerequisite edge as (courseId, prerequisiteId); the catalogue is small enough to load whole. */
    @Query(value = "select course_id, prerequisite_id from course_prerequisites", nativeQuery = true)
    List<Object[]> findAllPrerequisiteEdges();

    @Query("select c.id, count(p) from Course c join c.prerequisites p where c.id in :ids group by c.id")
    List<Object[]> countPrerequisites(@Param("ids") Collection<Long> ids);
}
