package com.unisphere.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.notification.NotificationWriter.Message;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationTextTest {

    @Test
    void abbreviatesLongTextWithAnEllipsis() {
        assertThat(NotificationWriter.abbreviate("  short  ", 10)).isEqualTo("short");
        assertThat(NotificationWriter.abbreviate("exactly 10", 10)).isEqualTo("exactly 10");
        assertThat(NotificationWriter.abbreviate("one two three", 9)).isEqualTo("one two…");
    }

    @Test
    void titlesNeverExceedTheColumn() {
        Message m = new Message(NotificationType.ANNOUNCEMENT, "x".repeat(500), "body", null);
        assertThat(m.title()).hasSize(NotificationWriter.TITLE_MAX).endsWith("…");
    }

    @Test
    void formatsTimesInUtcAndAmountsWithGrouping() {
        assertThat(Formats.dateTime(Instant.parse("2026-10-11T21:05:00Z"))).isEqualTo("11 Oct 2026, 21:05 UTC");
        assertThat(Formats.money("KES", new BigDecimal("1234567.5"))).isEqualTo("KES 1,234,567.50");
    }
}
