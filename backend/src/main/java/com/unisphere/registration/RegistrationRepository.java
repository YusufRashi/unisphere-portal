package com.unisphere.registration;

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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository extends JpaRepository<Registration, Long>, JpaSpecificationExecutor<Registration> {

    Optional<Registration> findByStudentIdAndSemesterId(Long studentId, Long semesterId);

    /**
     * Row-locks the registration (SELECT … FOR UPDATE) for the rest of the transaction, so two simultaneous
     * requests from the same student (double-clicks, two tabs) are applied one after the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.student.id = :studentId and r.semester.id = :semesterId")
    Optional<Registration> lockByStudentAndSemester(@Param("studentId") Long studentId, @Param("semesterId") Long semesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.id = :id")
    Optional<Registration> lockById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"student", "student.user", "student.program", "semester"})
    Page<Registration> findAll(Specification<Registration> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"semester"})
    List<Registration> findByStudentIdOrderBySemesterStartDateDesc(Long studentId);
}
