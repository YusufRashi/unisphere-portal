package com.unisphere.finance;

import com.unisphere.finance.FinanceEnums.PaymentMethod;
import java.math.BigDecimal;

/**
 * Port to an online payment provider (FIN-4). The application only ever sends an amount, a method and its
 * own reference; any card or wallet details are entered on the provider's side, so UniSphere never sees or
 * stores them (FIN-6). {@link SimulatedPaymentGateway} is the only implementation; a real provider plugs in
 * here.
 */
public interface PaymentGateway {

    record Request(String reference, BigDecimal amount, String currency, PaymentMethod method, String payerReference) {
    }

    /** {@code failureReason} is user-facing and set only when the payment was not approved. */
    record Result(boolean approved, String providerTxnId, String failureReason) {
    }

    /** Short provider name stored with each payment, e.g. "simulated". */
    String name();

    Result charge(Request request);
}
