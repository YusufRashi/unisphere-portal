package com.unisphere.finance;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/** Readable, unguessable references for online payments and simulated provider transactions. */
final class References {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private References() {
    }

    /** e.g. "UNI-20261004-9F3A61C2" */
    static String onlinePayment(Instant now) {
        return "UNI-" + DAY.format(now) + "-" + hex(4);
    }

    /** e.g. "SIM-7C0D2E91AB45" */
    static String simulatedTransaction() {
        return "SIM-" + hex(6);
    }

    private static String hex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().withUpperCase().formatHex(b);
    }
}
