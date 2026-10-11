package com.unisphere.finance;

import com.unisphere.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A student's fee account. Created on first use by an upsert; it has no stored balance. */
@Getter
@Entity
@Table(name = "fee_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    @Column(nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String currency;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
