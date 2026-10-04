package com.unisphere.lecturer;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LecturerRepository extends JpaRepository<Lecturer, Long>, JpaSpecificationExecutor<Lecturer> {

    @Override
    @EntityGraph(attributePaths = {"user", "department"})
    Page<Lecturer> findAll(Specification<Lecturer> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "department"})
    @Query("select l from Lecturer l where l.id = :id")
    Optional<Lecturer> findDetailedById(@Param("id") Long id);

    Optional<Lecturer> findByUserId(Long userId);
}
