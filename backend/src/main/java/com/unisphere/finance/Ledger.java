package com.unisphere.finance;

import com.unisphere.finance.FinanceEnums.ChargeStatus;
import com.unisphere.finance.FinanceEnums.PaymentMethod;
import com.unisphere.finance.FinanceEnums.PaymentStatus;
import com.unisphere.finance.dto.FinanceDtos.ChargeView;
import com.unisphere.finance.dto.FinanceDtos.PaymentView;
import com.unisphere.finance.dto.FinanceDtos.Summary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;

/**
 * Fee-ledger rules (FIN-1, FIN-4) as pure functions. The balance is always derived:
 * Σ active charges − Σ completed payments. Voided charges, failed attempts and reversed payments stay in
 * the history but never count.
 */
public final class Ledger {

    private Ledger() {
    }

    public static Summary summarize(Collection<ChargeView> charges, Collection<PaymentView> payments) {
        BigDecimal charged = charges.stream()
                .filter(c -> c.status() == ChargeStatus.ACTIVE)
                .map(ChargeView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = payments.stream()
                .filter(p -> p.status() == PaymentStatus.COMPLETED)
                .map(PaymentView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(money(charged), money(paid), money(charged.subtract(paid)));
    }

    /**
     * Why an online payment cannot be made, or empty if it can. Students pay at most what they owe online
     * (an overpayment would need a refund process); the finance office can still accept larger amounts.
     */
    public static Optional<String> onlinePaymentProblem(BigDecimal amount, PaymentMethod method, BigDecimal balance) {
        if (!method.onlineCapable()) {
            return Optional.of("Online payments can be made by mobile money or card only.");
        }
        if (balance.signum() <= 0) {
            return Optional.of("There is nothing to pay: your balance is " + money(balance).toPlainString() + ".");
        }
        if (amount.compareTo(balance) > 0) {
            return Optional.of("You can pay at most your outstanding balance of " + money(balance).toPlainString() + ".");
        }
        return Optional.empty();
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
