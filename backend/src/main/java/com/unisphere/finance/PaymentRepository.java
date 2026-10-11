package com.unisphere.finance;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByAccountIdOrderByPaidAtDescIdDesc(Long accountId);

    Optional<Payment> findByAccountIdAndIdempotencyKey(Long accountId, String idempotencyKey);

    @Query("select count(p) > 0 from Payment p where upper(p.reference) = upper(:reference)")
    boolean referenceExists(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.account a join fetch a.student where p.id = :id")
    Optional<Payment> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"account", "account.student", "account.student.user"})
    List<Payment> findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAscIdAsc(Instant from, Instant to);
}
