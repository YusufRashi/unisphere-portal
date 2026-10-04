package com.unisphere.transcript;

import com.unisphere.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A transcript content fingerprint that has been issued at least once. Written by an upsert, read here. */
@Getter
@Entity
@Table(name = "transcript_references")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranscriptReference {

    @Id
    @Column(length = 19)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "content_sha256", nullable = false, length = 64, columnDefinition = "bpchar(64)")
    private String contentSha256;

    @Column(precision = 3, scale = 2)
    private BigDecimal cgpa;

    @Column(name = "credits_earned", nullable = false)
    private int creditsEarned;

    @Column(name = "results_count", nullable = false)
    private int resultsCount;

    @Column(name = "first_issued_at", nullable = false)
    private Instant firstIssuedAt;

    @Column(name = "last_issued_at", nullable = false)
    private Instant lastIssuedAt;

    @Column(name = "issue_count", nullable = false)
    private int issueCount;
}
