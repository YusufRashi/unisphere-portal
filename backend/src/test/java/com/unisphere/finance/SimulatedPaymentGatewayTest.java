package com.unisphere.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.finance.FinanceEnums.PaymentMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

    private PaymentGateway.Result pay(String amount) {
        return gateway.charge(new PaymentGateway.Request("UNI-1", new BigDecimal(amount), "KES", PaymentMethod.MOBILE_MONEY, "S1"));
    }

    @Test
    void approvesNormalAmountsWithATransactionId() {
        PaymentGateway.Result result = pay("30000.00");
        assertThat(result.approved()).isTrue();
        assertThat(result.providerTxnId()).matches("^SIM-[0-9A-F]{12}$");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void declinesAmountsEndingIn13CentsOnPurpose() {
        PaymentGateway.Result result = pay("100.13");
        assertThat(result.approved()).isFalse();
        assertThat(result.failureReason()).contains("declined");
        assertThat(pay("13.00").approved()).isTrue();
    }

    @Test
    void transactionIdsAreUnique() {
        assertThat(pay("1.00").providerTxnId()).isNotEqualTo(pay("1.00").providerTxnId());
    }
}
