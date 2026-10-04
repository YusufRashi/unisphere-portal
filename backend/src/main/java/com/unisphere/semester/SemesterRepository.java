package com.unisphere.semester;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    List<Semester> findAllByOrderByStartDateDesc();

    Optional<Semester> findByCurrentTrue();

    @Query("select count(s) > 0 from Semester s where upper(s.code) = upper(:code) and (:excludeId is null or s.id <> :excludeId)")
    boolean codeTaken(@Param("code") String code, @Param("excludeId") Long excludeId);

    /** Clears the flag on every other semester so the partial unique index allows the new current one. */
    @Modifying(flushAutomatically = true)
    @Query("update Semester s set s.current = false where s.current = true and s.id <> :keepId")
    int clearCurrentExcept(@Param("keepId") Long keepId);
}
