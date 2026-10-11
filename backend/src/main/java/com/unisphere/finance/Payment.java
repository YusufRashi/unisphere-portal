package com.unisphere.finance;

import com.unisphere.finance.FinanceEnums.PaymentChannel;
import com.unisphere.finance.FinanceEnums.PaymentMethod;
import com.unisphere.finance.FinanceEnums.PaymentStatus;
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

/**
 * Money received, or an online attempt that failed. Never deleted or edited; a mistaken payment is
 * reversed with a reason. Holds no payment credentials: only the method, a reference and the provider's
 * transaction id.
 */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private FeeAccount account;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false, length = 60)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentChannel channel;

    @Column(length = 30)
    private String provider;

    @Column(name = "provider_txn_id", length = 80)
    private String providerTxnId;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(length = 200)
    private String note;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    /** A payment received at the finance office (cash desk, bank slip, card-terminal receipt). */
    static Payment atOffice(FeeAccount account, BigDecimal amount, PaymentMethod method, String reference, Instant paidAt,
                            String note, Long recordedBy) {
        Payment p = new Payment();
        p.account = account;
        p.amount = amount;
        p.method = method;
        p.reference = reference.trim();
        p.channel = PaymentChannel.OFFICE;
        p.status = PaymentStatus.COMPLETED;
        p.paidAt = paidAt;
        p.note = note == null || note.isBlank() ? null : note.trim();
        p.recordedBy = recordedBy;
        return p;
    }

    /** The outcome of an online payment through the gateway, approved or not. */
    static Payment online(FeeAccount account, BigDecimal amount, PaymentMethod method, String reference, String idempotencyKey,
                          String provider, PaymentGateway.Result result, Instant now) {
        Payment p = new Payment();
        p.account = account;
        p.amount = amount;
        p.method = method;
        p.reference = reference;
        p.channel = PaymentChannel.ONLINE;
        p.idempotencyKey = idempotencyKey;
        p.provider = provider;
        p.providerTxnId = result.providerTxnId();
        p.status = result.approved() ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;
        p.failureReason = result.approved() ? null : result.failureReason();
        p.paidAt = now;
        return p;
    }

    void reverse(Long by, Instant now, String reason) {
        if (status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Payment " + id + " is " + status + "; only completed payments can be reversed");
        }
        this.status = PaymentStatus.REVERSED;
        this.reversedBy = by;
        this.reversedAt = now;
        this.reversalReason = reason;
    }
}
