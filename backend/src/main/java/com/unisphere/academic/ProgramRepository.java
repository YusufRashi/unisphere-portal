package com.unisphere.academic;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    @Query("""
            select p from Program p join fetch p.department d
            where (:departmentId is null or d.id = :departmentId)
              and (:active is null or p.active = :active)
            order by p.name""")
    List<Program> search(@Param("departmentId") Long departmentId, @Param("active") Boolean active);

    @Query("select count(p) > 0 from Program p where upper(p.code) = upper(:code) and (:excludeId is null or p.id <> :excludeId)")
    boolean codeTaken(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Query("select s.program.id, count(s) from Student s group by s.program.id")
    List<Object[]> countStudentsByProgram();
}
