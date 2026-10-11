package com.unisphere.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.finance.FinanceEnums.ChargeKind;
import com.unisphere.finance.FinanceEnums.ChargeStatus;
import com.unisphere.finance.FinanceEnums.PaymentChannel;
import com.unisphere.finance.FinanceEnums.PaymentMethod;
import com.unisphere.finance.FinanceEnums.PaymentStatus;
import com.unisphere.finance.dto.FinanceDtos.ChargeView;
import com.unisphere.finance.dto.FinanceDtos.PaymentView;
import com.unisphere.finance.dto.FinanceDtos.Summary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerTest {

    private static ChargeView charge(String amount, ChargeStatus status) {
        return new ChargeView(1L, ChargeKind.MANUAL, "Charge", null, new BigDecimal(amount), status, Instant.EPOCH, null, null);
    }

    private static PaymentView payment(String amount, PaymentStatus status) {
        return new PaymentView(1L, new BigDecimal(amount), PaymentMethod.CASH, PaymentChannel.OFFICE, "R", status, null, Instant.EPOCH,
                null, null, null);
    }

    @Test
    void balanceIsActiveChargesMinusCompletedPayments() {
        Summary s = Ledger.summarize(
                List.of(charge("85000.00", ChargeStatus.ACTIVE), charge("1500.50", ChargeStatus.ACTIVE), charge("999.00", ChargeStatus.VOIDED)),
                List.of(payment("40000.00", PaymentStatus.COMPLETED), payment("100.13", PaymentStatus.FAILED),
                        payment("5000.00", PaymentStatus.REVERSED)));
        assertThat(s.totalCharged()).isEqualByComparingTo("86500.50");
        assertThat(s.totalPaid()).isEqualByComparingTo("40000.00");
        assertThat(s.balance()).isEqualByComparingTo("46500.50");
        assertThat(s.balance().scale()).isEqualTo(2);
    }

    @Test
    void decimalsAreExact() {
        // 0.1 + 0.2 is not 0.3 in floating point; it must be here.
        Summary s = Ledger.summarize(List.of(charge("0.10", ChargeStatus.ACTIVE), charge("0.20", ChargeStatus.ACTIVE)),
                List.of(payment("0.30", PaymentStatus.COMPLETED)));
        assertThat(s.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void overpaymentShowsAsCredit() {
        Summary s = Ledger.summarize(List.of(charge("100.00", ChargeStatus.ACTIVE)), List.of(payment("150.00", PaymentStatus.COMPLETED)));
        assertThat(s.balance()).isEqualByComparingTo("-50.00");
    }

    @Test
    void anEmptyAccountOwesNothing() {
        assertThat(Ledger.summarize(List.of(), List.of())).isEqualTo(
                new Summary(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")));
    }

    @Test
    void onlinePaymentsAreLimitedToTheBalanceAndOnlineMethods() {
        BigDecimal balance = new BigDecimal("500.00");
        assertThat(Ledger.onlinePaymentProblem(new BigDecimal("500.00"), PaymentMethod.MOBILE_MONEY, balance)).isEmpty();
        assertThat(Ledger.onlinePaymentProblem(new BigDecimal("0.01"), PaymentMethod.CARD, balance)).isEmpty();
        assertThat(Ledger.onlinePaymentProblem(new BigDecimal("500.01"), PaymentMethod.CARD, balance))
                .contains("You can pay at most your outstanding balance of 500.00.");
        assertThat(Ledger.onlinePaymentProblem(new BigDecimal("10.00"), PaymentMethod.CASH, balance))
                .contains("Online payments can be made by mobile money or card only.");
        assertThat(Ledger.onlinePaymentProblem(new BigDecimal("10.00"), PaymentMethod.CARD, new BigDecimal("-20.00")))
                .contains("There is nothing to pay: your balance is -20.00.");
    }
}
