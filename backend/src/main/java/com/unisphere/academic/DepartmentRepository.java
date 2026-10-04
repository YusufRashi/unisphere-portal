package com.unisphere.academic;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderByNameAsc();

    @Query("select count(d) > 0 from Department d where upper(d.code) = upper(:code) and (:excludeId is null or d.id <> :excludeId)")
    boolean codeTaken(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Query("select count(d) > 0 from Department d where lower(d.name) = lower(:name) and (:excludeId is null or d.id <> :excludeId)")
    boolean nameTaken(@Param("name") String name, @Param("excludeId") Long excludeId);

    /** Rows of (departmentId, count) — one grouped query instead of one count per department. */
    @Query("select p.department.id, count(p) from Program p group by p.department.id")
    List<Object[]> countProgramsByDepartment();

    @Query("select c.department.id, count(c) from Course c group by c.department.id")
    List<Object[]> countCoursesByDepartment();

    @Query("select l.department.id, count(l) from Lecturer l group by l.department.id")
    List<Object[]> countLecturersByDepartment();
}
