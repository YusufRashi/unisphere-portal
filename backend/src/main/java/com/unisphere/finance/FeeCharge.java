package com.unisphere.finance;

import com.unisphere.finance.FinanceEnums.ChargeKind;
import com.unisphere.finance.FinanceEnums.ChargeStatus;
import com.unisphere.semester.Semester;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An amount the student owes. Never deleted or edited; a mistaken charge is voided with a reason. */
@Getter
@Entity
@Table(name = "fee_charges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private FeeAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @Column(name = "fee_structure_id")
    private Long feeStructureId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeKind kind;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeStatus status = ChargeStatus.ACTIVE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Version
    private long version;

    /** A manual charge. Tuition charges are inserted by an idempotent upsert instead. */
    static FeeCharge manual(FeeAccount account, Semester semester, String description, BigDecimal amount, Long createdBy,
                            Instant now) {
        FeeCharge c = new FeeCharge();
        c.account = account;
        c.semester = semester;
        c.kind = ChargeKind.MANUAL;
        c.description = description.trim();
        c.amount = amount;
        c.createdBy = createdBy;
        c.createdAt = now;
        return c;
    }

    public boolean isActive() {
        return status == ChargeStatus.ACTIVE;
    }

    void voidCharge(Long by, Instant now, String reason) {
        if (!isActive()) {
            throw new IllegalStateException("Charge " + id + " is already voided");
        }
        this.status = ChargeStatus.VOIDED;
        this.voidedBy = by;
        this.voidedAt = now;
        this.voidReason = reason;
    }
}
