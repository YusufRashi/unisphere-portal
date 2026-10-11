package com.unisphere.finance;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.finance.FinanceEnums.ChargeStatus;
import com.unisphere.finance.FinanceEnums.PaymentStatus;
import com.unisphere.finance.dto.FinanceDtos.AccountView;
import com.unisphere.finance.dto.FinanceDtos.ChargeRequest;
import com.unisphere.finance.dto.FinanceDtos.ChargeView;
import com.unisphere.finance.dto.FinanceDtos.OfficePaymentRequest;
import com.unisphere.finance.dto.FinanceDtos.OnlinePaymentRequest;
import com.unisphere.finance.dto.FinanceDtos.OnlinePaymentResult;
import com.unisphere.finance.dto.FinanceDtos.PaymentView;
import com.unisphere.finance.dto.FinanceDtos.StudentRef;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterService;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's fee account: statement, manual charges, payments and corrections (FIN-1, FIN-3, FIN-4).
 * Every change locks the account row first, so balance checks and duplicate detection cannot race.
 */
@Service
@RequiredArgsConstructor
public class FeeAccountService {

    private final StudentRepository students;
    private final FeeAccountRepository accounts;
    private final FeeChargeRepository charges;
    private final PaymentRepository payments;
    private final SemesterService semesters;
    private final PaymentGateway gateway;
    private final AuditService audit;
    private final UniSphereProperties properties;
    private final Clock clock;

    // ---- Statements ----

    @Transactional(readOnly = true)
    public AccountView myAccount() {
        return view(currentStudent());
    }

    @Transactional(readOnly = true)
    public AccountView account(Long studentId) {
        return view(loadStudent(studentId));
    }

    // ---- Finance office ----

    @Transactional
    public AccountView addCharge(Long studentId, ChargeRequest r) {
        Student student = loadStudent(studentId);
        FeeAccount account = lockAccount(student);
        Semester semester = r.semesterId() == null ? null : semesters.load(r.semesterId());
        FeeCharge charge = charges.save(FeeCharge.manual(account, semester, r.description(), r.amount(), CurrentUser.id(), clock.instant()));
        audit.record(AuditAction.CHARGE_ADDED, "FeeCharge", charge.getId(),
                Map.of("student", student.getStudentNumber(), "amount", r.amount(), "description", charge.getDescription()));
        return view(student);
    }

    @Transactional
    public AccountView voidCharge(Long chargeId, String reason) {
        FeeCharge charge = charges.lockById(chargeId).orElseThrow(() -> ApiException.notFound("Charge", chargeId));
        if (!charge.isActive()) {
            throw ApiException.invalidState("This charge has already been voided.");
        }
        charge.voidCharge(CurrentUser.id(), clock.instant(), reason.trim());
        Student student = charge.getAccount().getStudent();
        audit.record(AuditAction.CHARGE_VOIDED, "FeeCharge", chargeId,
                Map.of("student", student.getStudentNumber(), "amount", charge.getAmount(), "reason", reason.trim()));
        return view(student);
    }

    @Transactional
    public AccountView recordPayment(Long studentId, OfficePaymentRequest r) {
        Instant now = clock.instant();
        Instant paidAt = r.paidAt() == null ? now : r.paidAt();
        if (paidAt.isAfter(now)) {
            throw ApiException.validation("paidAt", "cannot be in the future");
        }
        Student student = loadStudent(studentId);
        FeeAccount account = lockAccount(student);
        String reference = r.reference().trim();
        if (payments.referenceExists(reference)) {
            throw ApiException.duplicate("Payment reference", reference)
                    .with("errors", List.of(Map.of("field", "reference", "message", "has already been recorded")));
        }
        Payment payment = payments.save(Payment.atOffice(account, r.amount(), r.method(), reference, paidAt, r.note(), CurrentUser.id()));
        audit.record(AuditAction.PAYMENT_RECORDED, "Payment", payment.getId(),
                Map.of("student", student.getStudentNumber(), "amount", r.amount(), "reference", reference, "method", r.method().name()));
        return view(student);
    }

    @Transactional
    public AccountView reversePayment(Long paymentId, String reason) {
        Payment payment = payments.lockById(paymentId).orElseThrow(() -> ApiException.notFound("Payment", paymentId));
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw ApiException.invalidState("Only completed payments can be reversed (this one is %s)."
                    .formatted(payment.getStatus().name().toLowerCase()));
        }
        payment.reverse(CurrentUser.id(), clock.instant(), reason.trim());
        Student student = payment.getAccount().getStudent();
        audit.record(AuditAction.PAYMENT_REVERSED, "Payment", paymentId,
                Map.of("student", student.getStudentNumber(), "amount", payment.getAmount(), "reference", payment.getReference(),
                        "reason", reason.trim()));
        return view(student);
    }

    // ---- Student ----

    /**
     * Pays through the gateway. A repeated request with the same idempotency key returns the first outcome
     * instead of paying again (double clicks, retries after a timeout). A declined payment is recorded as
     * FAILED and changes nothing else.
     */
    @Transactional
    public OnlinePaymentResult payOnline(OnlinePaymentRequest r) {
        Student student = currentStudent();
        FeeAccount account = lockAccount(student);
        Optional<Payment> replay = payments.findByAccountIdAndIdempotencyKey(account.getId(), r.idempotencyKey());
        if (replay.isPresent()) {
            return new OnlinePaymentResult(paymentView(replay.get()), view(student));
        }
        AccountView before = view(student);
        Ledger.onlinePaymentProblem(r.amount(), r.method(), before.summary().balance()).ifPresent(problem -> {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, problem);
        });

        Instant now = clock.instant();
        String reference = References.onlinePayment(now);
        PaymentGateway.Result result = gateway.charge(new PaymentGateway.Request(reference, r.amount(), account.getCurrency(), r.method(),
                student.getStudentNumber()));
        Payment payment = payments.save(Payment.online(account, r.amount(), r.method(), reference, r.idempotencyKey(), gateway.name(),
                result, now));
        audit.record(AuditAction.PAYMENT_ATTEMPTED, "Payment", payment.getId(),
                Map.of("amount", r.amount(), "reference", reference, "status", payment.getStatus().name()));
        return new OnlinePaymentResult(paymentView(payment), view(student));
    }

    // ---- Helpers ----

    private Student currentStudent() {
        return students.findByUserId(CurrentUser.id())
                .orElseThrow(() -> ApiException.forbidden("Only students have a fee account."));
    }

    private Student loadStudent(Long studentId) {
        return students.findDetailedById(studentId).orElseThrow(() -> ApiException.notFound("Student", studentId));
    }

    /** Creates the account on first use, then locks it for the rest of the transaction. */
    private FeeAccount lockAccount(Student student) {
        accounts.ensure(student.getId(), properties.finance().currency());
        return accounts.lockByStudentId(student.getId()).orElseThrow();
    }

    AccountView view(Student student) {
        StudentRef ref = new StudentRef(student.getId(), student.getStudentNumber(), student.getUser().getFullName(),
                student.getUser().getEmail(), student.getProgram().getCode(), student.getProgram().getName());
        Optional<FeeAccount> account = accounts.findByStudentId(student.getId());
        List<ChargeView> chargeViews = account
                .map(a -> charges.findByAccountIdOrderByCreatedAtDescIdDesc(a.getId()).stream().map(FeeAccountService::chargeView).toList())
                .orElse(List.of());
        List<PaymentView> paymentViews = account
                .map(a -> payments.findByAccountIdOrderByPaidAtDescIdDesc(a.getId()).stream().map(FeeAccountService::paymentView).toList())
                .orElse(List.of());
        String currency = account.map(FeeAccount::getCurrency).orElse(properties.finance().currency());
        return new AccountView(ref, currency, Ledger.summarize(chargeViews, paymentViews), chargeViews, paymentViews);
    }

    static ChargeView chargeView(FeeCharge c) {
        return new ChargeView(c.getId(), c.getKind(), c.getDescription(), c.getSemester() == null ? null : SemesterRef.from(c.getSemester()),
                c.getAmount(), c.getStatus(), c.getCreatedAt(), c.getVoidedAt(),
                c.getStatus() == ChargeStatus.VOIDED ? c.getVoidReason() : null);
    }

    static PaymentView paymentView(Payment p) {
        return new PaymentView(p.getId(), p.getAmount(), p.getMethod(), p.getChannel(), p.getReference(), p.getStatus(),
                p.getFailureReason(), p.getPaidAt(), p.getNote(), p.getReversedAt(), p.getReversalReason());
    }
}
