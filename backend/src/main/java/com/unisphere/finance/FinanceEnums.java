package com.unisphere.finance;

/** Holder for the small finance enums, kept together because each is only a few constants. */
public final class FinanceEnums {

    private FinanceEnums() {
    }

    /** TUITION charges are generated from a fee structure; MANUAL ones are added by finance staff. */
    public enum ChargeKind {
        TUITION,
        MANUAL
    }

    public enum ChargeStatus {
        ACTIVE,
        VOIDED
    }

    /** How the money was paid. Only the method and a reference are stored, never card or account details. */
    public enum PaymentMethod {
        CASH,
        BANK_TRANSFER,
        MOBILE_MONEY,
        CARD;

        /** Methods a student can use online, through the payment gateway. */
        public boolean onlineCapable() {
            return this == MOBILE_MONEY || this == CARD;
        }
    }

    /** OFFICE: recorded by finance staff from a receipt. ONLINE: paid by the student through the gateway. */
    public enum PaymentChannel {
        OFFICE,
        ONLINE
    }

    /** Only COMPLETED payments reduce the balance; FAILED attempts and REVERSED payments are kept for history. */
    public enum PaymentStatus {
        COMPLETED,
        FAILED,
        REVERSED
    }
}
