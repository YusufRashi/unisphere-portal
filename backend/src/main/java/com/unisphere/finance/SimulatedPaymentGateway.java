package com.unisphere.finance;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * A stand-in provider for demos and tests. It approves every payment except amounts ending in {@code .13},
 * which it declines, so the failure path can be demonstrated on purpose (like a payment provider's test
 * card numbers).
 */
@Component
class SimulatedPaymentGateway implements PaymentGateway {

    static final BigDecimal DECLINED_CENTS = new BigDecimal("0.13");

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public Result charge(Request request) {
        String txn = References.simulatedTransaction();
        if (request.amount().remainder(BigDecimal.ONE).compareTo(DECLINED_CENTS) == 0) {
            return new Result(false, txn, "The payment was declined by the provider. No money was taken.");
        }
        return new Result(true, txn, null);
    }
}
