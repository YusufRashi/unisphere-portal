package com.unisphere.finance.dto;

import com.unisphere.finance.FinanceEnums.ChargeKind;
import com.unisphere.finance.FinanceEnums.ChargeStatus;
import com.unisphere.finance.FinanceEnums.PaymentChannel;
import com.unisphere.finance.FinanceEnums.PaymentMethod;
import com.unisphere.finance.FinanceEnums.PaymentStatus;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    // ---- Requests ----

    public record ChargeRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("10000000") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            Long semesterId) {
    }

    /** A payment received at the finance office. {@code paidAt} defaults to now and may not be in the future. */
    public record OfficePaymentRequest(
            @NotNull @DecimalMin("0.01") @DecimalMax("10000000") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull PaymentMethod method,
            @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9/_.-]*$",
                    message = "may contain only letters, digits and / _ . -") String reference,
            Instant paidAt,
            @Size(max = 200) String note) {
    }

    /**
     * An online payment by the student. {@code idempotencyKey} is generated once per payment attempt by the
     * client; sending the same key again returns the first outcome instead of paying twice.
     */
    public record OnlinePaymentRequest(
            @NotNull @DecimalMin("0.01") @DecimalMax("10000000") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull PaymentMethod method,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$", message = "must be 8–64 letters, digits or hyphens")
            String idempotencyKey) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record FeeStructureRequest(
            @NotNull Long programId,
            @NotNull Long semesterId,
            @NotNull @DecimalMin("0.01") @DecimalMax("10000000") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 120) String description) {
    }

    // ---- Account statement ----

    public record StudentRef(Long id, String studentNumber, String name, String email, String programCode, String programName) {
    }

    public record ChargeView(
            Long id, ChargeKind kind, String description, SemesterRef semester, BigDecimal amount, ChargeStatus status,
            Instant createdAt, Instant voidedAt, String voidReason) {
    }

    public record PaymentView(
            Long id, BigDecimal amount, PaymentMethod method, PaymentChannel channel, String reference, PaymentStatus status,
            String failureReason, Instant paidAt, String note, Instant reversedAt, String reversalReason) {
    }

    /** {@code balance} > 0 means money owed; < 0 means the student is in credit. */
    public record Summary(BigDecimal totalCharged, BigDecimal totalPaid, BigDecimal balance) {
    }

    /** Charges and payments newest first. */
    public record AccountView(StudentRef student, String currency, Summary summary, List<ChargeView> charges,
                              List<PaymentView> payments) {
    }

    /** {@code payment.status} is FAILED when the provider declined; nothing was taken and the balance is unchanged. */
    public record OnlinePaymentResult(PaymentView payment, AccountView account) {
    }

    // ---- Lists and reports ----

    public record AccountRow(
            Long studentId, String studentNumber, String name, String email, String programCode, String currency,
            BigDecimal totalCharged, BigDecimal totalPaid, BigDecimal balance) {
    }

    public record ProgramRef(Long id, String code, String name) {
    }

    public record StructureView(Long id, BigDecimal amount, String description) {
    }

    /** One row per active program; {@code structure} is null when no fee is set for the semester. */
    public record FeeStructureRow(ProgramRef program, StructureView structure, long chargedCount) {
    }

    public record FeeStructures(SemesterRef semester, String currency, List<FeeStructureRow> rows) {
    }

    /**
     * What generating charges for a semester would do: registered (approved) students, those already billed,
     * those who would be billed now, and programs with registered students but no fee set.
     */
    public record ChargePreview(
            SemesterRef semester, String currency, int registeredStudents, int alreadyCharged, int toCharge,
            BigDecimal amountToCharge, int withoutFee, List<String> programsWithoutFee) {
    }

    public record GenerationResult(int charged, int alreadyCharged, int withoutFee, BigDecimal amountCharged) {
    }

    public record PaymentReportRow(
            Long paymentId, Instant paidAt, String reference, String studentNumber, String name, PaymentMethod method,
            PaymentChannel channel, PaymentStatus status, BigDecimal amount) {
    }

    public record PaymentReport(String currency, Instant from, Instant to, BigDecimal totalCompleted, List<PaymentReportRow> rows) {
    }
}
