package com.unisphere.student;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    @Override
    @EntityGraph(attributePaths = {"user", "program", "program.department"})
    Page<Student> findAll(Specification<Student> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "program", "program.department"})
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findDetailedById(@Param("id") Long id);

    Optional<Student> findByUserId(Long userId);
}
