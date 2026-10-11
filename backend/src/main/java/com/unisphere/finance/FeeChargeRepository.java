package com.unisphere.finance;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FeeChargeRepository extends JpaRepository<FeeCharge, Long> {

    @EntityGraph(attributePaths = "semester")
    List<FeeCharge> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FeeCharge c join fetch c.account a join fetch a.student where c.id = :id")
    Optional<FeeCharge> lockById(@Param("id") Long id);

    /**
     * Bills one student for a fee structure. Returns 0 when that student already has a charge for it
     * (voided or not), so generating charges again never bills anyone twice.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into fee_charges (account_id, semester_id, fee_structure_id, kind, description, amount, created_by, created_at)
            values (:accountId, :semesterId, :structureId, 'TUITION', :description, :amount, :createdBy, :now)
            on conflict (account_id, fee_structure_id) where fee_structure_id is not null do nothing""", nativeQuery = true)
    int insertTuition(@Param("accountId") Long accountId, @Param("semesterId") Long semesterId,
                      @Param("structureId") Long structureId, @Param("description") String description,
                      @Param("amount") BigDecimal amount, @Param("createdBy") Long createdBy, @Param("now") Instant now);

    long countByFeeStructureId(Long feeStructureId);

    /** Students already billed for any of the structures. */
    @Query("select c.account.student.id from FeeCharge c where c.feeStructureId in :structureIds")
    List<Long> findStudentIdsChargedFor(@Param("structureIds") Collection<Long> structureIds);

    /** [structureId, count] pairs. */
    @Query("select c.feeStructureId, count(c) from FeeCharge c where c.feeStructureId in :structureIds group by c.feeStructureId")
    List<Object[]> countByStructures(@Param("structureIds") Collection<Long> structureIds);
}
