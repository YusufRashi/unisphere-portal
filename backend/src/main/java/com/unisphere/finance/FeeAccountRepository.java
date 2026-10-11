package com.unisphere.finance;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FeeAccountRepository extends JpaRepository<FeeAccount, Long> {

    /** Creates the account if it does not exist yet; safe when two requests do it at the same time. */
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into fee_accounts (student_id, currency) values (:studentId, :currency) on conflict (student_id) do nothing",
            nativeQuery = true)
    int ensure(@Param("studentId") Long studentId, @Param("currency") String currency);

    Optional<FeeAccount> findByStudentId(Long studentId);

    /** Serialises payments on one account, so balance checks and idempotency look-ups cannot race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FeeAccount a where a.student.id = :studentId")
    Optional<FeeAccount> lockByStudentId(@Param("studentId") Long studentId);
}
