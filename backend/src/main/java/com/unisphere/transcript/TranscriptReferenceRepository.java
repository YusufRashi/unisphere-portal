package com.unisphere.transcript;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TranscriptReferenceRepository extends JpaRepository<TranscriptReference, String> {

    /** Records an issue in one atomic statement, so simultaneous downloads of the same record cannot collide. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into transcript_references
                (reference, student_id, content_sha256, cgpa, credits_earned, results_count, first_issued_at, last_issued_at)
            values (:reference, :studentId, :sha256, cast(:cgpa as numeric(3, 2)), :creditsEarned, :resultsCount, :now, :now)
            on conflict (reference) do update
                set last_issued_at = excluded.last_issued_at,
                    issue_count = transcript_references.issue_count + 1""", nativeQuery = true)
    void recordIssue(@Param("reference") String reference, @Param("studentId") Long studentId, @Param("sha256") String sha256,
                     @Param("cgpa") BigDecimal cgpa, @Param("creditsEarned") int creditsEarned,
                     @Param("resultsCount") int resultsCount, @Param("now") Instant now);

    @EntityGraph(attributePaths = {"student", "student.user"})
    Optional<TranscriptReference> findByReference(String reference);
}
