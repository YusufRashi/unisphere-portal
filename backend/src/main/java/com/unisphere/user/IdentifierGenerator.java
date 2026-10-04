package com.unisphere.user;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Human-readable identifiers backed by PostgreSQL sequences, so concurrent account creation can never
 * produce duplicates. Examples: student {@code S2026-00042}, staff {@code STF-00007}.
 */
@Component
public class IdentifierGenerator {

    private final EntityManager entityManager;

    public IdentifierGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public String nextStudentNumber(int admissionYear) {
        return "S%d-%05d".formatted(admissionYear, next("student_number_seq"));
    }

    public String nextStaffNumber() {
        return "STF-%05d".formatted(next("staff_number_seq"));
    }

    private long next(String sequence) {
        // The sequence name is a constant from this class, never user input.
        Object value = entityManager.createNativeQuery("select nextval('" + sequence + "')").getSingleResult();
        return ((Number) value).longValue();
    }
}
