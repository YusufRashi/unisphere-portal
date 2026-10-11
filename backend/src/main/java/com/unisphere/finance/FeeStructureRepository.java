package com.unisphere.finance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {

    @EntityGraph(attributePaths = "program")
    List<FeeStructure> findBySemesterId(Long semesterId);

    Optional<FeeStructure> findByProgramIdAndSemesterId(Long programId, Long semesterId);
}
