package com.unisphere.finance;

import com.unisphere.common.web.CsvWriter;
import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import com.unisphere.finance.dto.FinanceDtos.AccountRow;
import com.unisphere.finance.dto.FinanceDtos.AccountView;
import com.unisphere.finance.dto.FinanceDtos.ChargePreview;
import com.unisphere.finance.dto.FinanceDtos.ChargeRequest;
import com.unisphere.finance.dto.FinanceDtos.FeeStructureRequest;
import com.unisphere.finance.dto.FinanceDtos.FeeStructures;
import com.unisphere.finance.dto.FinanceDtos.GenerationResult;
import com.unisphere.finance.dto.FinanceDtos.OfficePaymentRequest;
import com.unisphere.finance.dto.FinanceDtos.OnlinePaymentRequest;
import com.unisphere.finance.dto.FinanceDtos.OnlinePaymentResult;
import com.unisphere.finance.dto.FinanceDtos.PaymentReport;
import com.unisphere.finance.dto.FinanceDtos.ReasonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fees. Students see and pay their own account; finance staff manage every account, fee structures and
 * billing; registrars have read-only access to accounts, fee structures and reports.
 */
@Tag(name = "Fees & payments")
@RestController
@RequestMapping("/api/fees")
@RequiredArgsConstructor
public class FinanceController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final FeeAccountService accounts;
    private final FeeStructureService structures;
    private final FinanceReportService reports;
    private final Clock clock;

    // ---- Student ----

    @Operation(summary = "My fee account: totals, balance, charges and payments")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public AccountView mine() {
        return accounts.myAccount();
    }

    @Operation(summary = "Pay online through the (simulated) gateway; repeat-safe with an idempotency key")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/me/payments/simulate")
    public OnlinePaymentResult payOnline(@Valid @RequestBody OnlinePaymentRequest body) {
        return accounts.payOnline(body);
    }

    // ---- Accounts ----

    @Operation(summary = "Students with their balances; filter by program and outstanding balance, sort by balance")
    @PreAuthorize("hasAnyRole('FINANCE', 'ADMIN')")
    @GetMapping("/accounts")
    public PageResponse<AccountRow> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Boolean hasBalance,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, FinanceReportService.ACCOUNT_SORTS,
                Sort.by(Sort.Order.desc("x.balance"), Sort.Order.asc("x.student_number")));
        return reports.search(q, programId, hasBalance, pageable);
    }

    @PreAuthorize("hasAnyRole('FINANCE', 'ADMIN')")
    @GetMapping("/accounts/{studentId}")
    public AccountView account(@PathVariable Long studentId) {
        return accounts.account(studentId);
    }

    @Operation(summary = "Add a manual charge")
    @PreAuthorize("hasRole('FINANCE')")
    @PostMapping("/accounts/{studentId}/charges")
    public AccountView addCharge(@PathVariable Long studentId, @Valid @RequestBody ChargeRequest body) {
        return accounts.addCharge(studentId, body);
    }

    @Operation(summary = "Record a payment received at the office (409 if the reference was already recorded)")
    @PreAuthorize("hasRole('FINANCE')")
    @PostMapping("/accounts/{studentId}/payments")
    public AccountView recordPayment(@PathVariable Long studentId, @Valid @RequestBody OfficePaymentRequest body) {
        return accounts.recordPayment(studentId, body);
    }

    @Operation(summary = "Reverse a completed payment with a reason (it stays in the history)")
    @PreAuthorize("hasRole('FINANCE')")
    @PostMapping("/payments/{paymentId}/reverse")
    public AccountView reversePayment(@PathVariable Long paymentId, @Valid @RequestBody ReasonRequest body) {
        return accounts.reversePayment(paymentId, body.reason());
    }

    @Operation(summary = "Void a charge with a reason (it stays in the history)")
    @PreAuthorize("hasRole('FINANCE')")
    @PostMapping("/charges/{chargeId}/void")
    public AccountView voidCharge(@PathVariable Long chargeId, @Valid @RequestBody ReasonRequest body) {
        return accounts.voidCharge(chargeId, body.reason());
    }

    // ---- Fee structures and billing ----

    @Operation(summary = "Fee per program for a semester")
    @PreAuthorize("hasAnyRole('FINANCE', 'ADMIN')")
    @GetMapping("/structures")
    public FeeStructures structures(@RequestParam Long semesterId) {
        return structures.list(semesterId);
    }

    @Operation(summary = "Set the fee for a program in a semester (amount is fixed once charged)")
    @PreAuthorize("hasRole('FINANCE')")
    @PutMapping("/structures")
    public FeeStructures saveStructure(@Valid @RequestBody FeeStructureRequest body) {
        return structures.save(body);
    }

    @PreAuthorize("hasRole('FINANCE')")
    @DeleteMapping("/structures/{structureId}")
    public ResponseEntity<Void> deleteStructure(@PathVariable Long structureId) {
        structures.delete(structureId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "What generating charges would do for a semester")
    @PreAuthorize("hasRole('FINANCE')")
    @GetMapping("/semesters/{semesterId}/charges/preview")
    public ChargePreview preview(@PathVariable Long semesterId) {
        return structures.preview(semesterId);
    }

    @Operation(summary = "Charge every registered student their program's fee (safe to run again)")
    @PreAuthorize("hasRole('FINANCE')")
    @PostMapping("/semesters/{semesterId}/generate-charges")
    public GenerationResult generate(@PathVariable Long semesterId) {
        return structures.generate(semesterId);
    }

    // ---- Reports ----

    @Operation(summary = "Students who owe money, largest balance first; format=csv for a spreadsheet")
    @PreAuthorize("hasAnyRole('FINANCE', 'ADMIN')")
    @GetMapping("/reports/outstanding")
    public ResponseEntity<?> outstanding(@RequestParam(required = false) Long programId,
                                         @RequestParam(required = false) String format) {
        List<AccountRow> rows = reports.outstanding(programId);
        if (!"csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(rows);
        }
        String csv = CsvWriter.write(List.of("Student number", "Name", "Email", "Program", "Currency", "Charged", "Paid", "Balance"),
                rows.stream().<List<Object>>map(r -> List.of(r.studentNumber(), r.name(), r.email(), r.programCode(), r.currency(),
                        r.totalCharged(), r.totalPaid(), r.balance())).toList());
        return csv(csv, "outstanding-balances-" + today() + ".csv");
    }

    @Operation(summary = "Payments in a date range (inclusive, UTC); format=csv for a spreadsheet")
    @PreAuthorize("hasAnyRole('FINANCE', 'ADMIN')")
    @GetMapping("/reports/payments")
    public ResponseEntity<?> payments(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) String format) {
        PaymentReport report = reports.payments(from, to);
        if (!"csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(report);
        }
        String csv = CsvWriter.write(List.of("Paid at (UTC)", "Reference", "Student number", "Name", "Method", "Channel", "Status",
                        "Currency", "Amount"),
                report.rows().stream().<List<Object>>map(r -> List.of(r.paidAt(), r.reference(), r.studentNumber(), r.name(),
                        r.method().name(), r.channel().name(), r.status().name(), report.currency(), r.amount())).toList());
        return csv(csv, "payments-" + from + "-to-" + to + ".csv");
    }

    private static ResponseEntity<String> csv(String body, String filename) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
