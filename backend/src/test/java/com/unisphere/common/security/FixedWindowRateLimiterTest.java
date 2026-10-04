package com.unisphere.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T10:00:05Z"));
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, clock);

    @Test
    void allowsRequestsUpToTheLimit() {
        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
    }

    @Test
    void rejectsRequestsOverTheLimitWithSecondsUntilReset() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        // Window started at 10:00:00, now is 10:00:05 → 55 seconds remain.
        assertThat(limiter.tryAcquire("1.2.3.4")).isEqualTo(55);
    }

    @Test
    void countsKeysIndependently() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        assertThat(limiter.tryAcquire("5.6.7.8")).isZero();
    }

    @Test
    void resetsInTheNextWindow() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
    }
}
