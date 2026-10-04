package com.unisphere.common.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory fixed-window limiter keyed by client (e.g. IP + endpoint group).
 *
 * <p>Sufficient for a single API instance. With several instances behind a load balancer this would move
 * to a shared store (Redis) or to the edge proxy — the {@link #tryAcquire} contract stays the same.
 */
public class FixedWindowRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limitPerWindow;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limitPerWindow, Clock clock) {
        this.limitPerWindow = limitPerWindow;
        this.clock = clock;
    }

    /** @return 0 if the request is allowed, otherwise the number of seconds until the window resets. */
    public long tryAcquire(String key) {
        long now = clock.millis();
        long windowStart = now - (now % WINDOW_MILLIS);
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.start < windowStart);
        }
        Window window = windows.compute(key, (k, existing) ->
                existing == null || existing.start != windowStart
                        ? new Window(windowStart, 1)
                        : new Window(existing.start, existing.count + 1));
        if (window.count <= limitPerWindow) {
            return 0;
        }
        return Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
    }

    private record Window(long start, int count) {
    }
}
