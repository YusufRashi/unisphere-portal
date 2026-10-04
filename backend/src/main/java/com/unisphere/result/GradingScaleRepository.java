package com.unisphere.result;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface GradingScaleRepository extends JpaRepository<GradingScale, Long> {

    @EntityGraph(attributePaths = "bands")
    Optional<GradingScale> findByActiveTrue();

    /** Serialises concurrent scale replacements. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GradingScale s where s.active = true")
    Optional<GradingScale> lockActive();
}
