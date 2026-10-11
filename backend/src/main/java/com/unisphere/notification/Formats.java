package com.unisphere.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dates and amounts inside notification text. Times are in UTC, like everywhere else in the system. */
final class Formats {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private Formats() {
    }

    static String dateTime(Instant instant) {
        return DATE_TIME.format(instant);
    }

    static String money(String currency, BigDecimal amount) {
        return String.format(Locale.ENGLISH, "%s %,.2f", currency, amount);
    }
}
