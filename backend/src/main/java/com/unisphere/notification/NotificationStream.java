package com.unisphere.notification;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes a user's unread count to their open tabs the moment it changes (Server-Sent Events), so the bell updates
 * without waiting for the next poll.
 *
 * <p>A stream closes before the access token that opened it expires, and the browser reconnects with a fresh one,
 * so a session that has been signed out or revoked cannot keep listening. Streams live in this instance's memory:
 * with several API instances a user only hears about changes made through the instance they are connected to,
 * and the browser's slow polling covers the rest.
 */
@Component
public class NotificationStream {

    private static final Logger log = LoggerFactory.getLogger(NotificationStream.class);
    static final Duration MAX_OPEN = Duration.ofMinutes(10);
    static final String EVENT = "unread";

    private final Map<Long, Set<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final NotificationRepository notifications;
    private final Clock clock;

    NotificationStream(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Opens a stream for {@code userId} that ends when the token does (or after {@link #MAX_OPEN}). */
    public SseEmitter open(Long userId, Instant tokenExpiresAt) {
        Duration untilExpiry = Duration.between(clock.instant(), tokenExpiresAt);
        long timeout = Math.max(1_000, Math.min(MAX_OPEN.toMillis(), untilExpiry.toMillis()));
        SseEmitter emitter = new SseEmitter(timeout);
        Set<SseEmitter> mine = streams.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet());
        mine.add(emitter);
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(error -> remove.run());
        send(userId, emitter, notifications.countByUserIdAndReadAtIsNull(userId));
        return emitter;
    }

    /** Tells these users' open tabs their new unread count; after the current transaction commits, if there is one. */
    public void changed(Collection<Long> userIds) {
        if (userIds.isEmpty() || streams.isEmpty()) {
            return;
        }
        Set<Long> recipients = Set.copyOf(userIds);
        Runnable push = () -> recipients.stream().filter(streams::containsKey).forEach(this::push);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    /** Keeps idle connections open through proxies, and finds the ones whose tab has gone. */
    @Scheduled(fixedDelay = 25_000, initialDelay = 25_000)
    void heartbeat() {
        streams.forEach((userId, mine) -> mine.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }));
    }

    int openStreams() {
        return streams.values().stream().mapToInt(Set::size).sum();
    }

    private void push(Long userId) {
        long unread = notifications.countByUserIdAndReadAtIsNull(userId);
        streams.getOrDefault(userId, Set.of()).forEach(emitter -> send(userId, emitter, unread));
    }

    private void send(Long userId, SseEmitter emitter, long unread) {
        try {
            emitter.send(SseEmitter.event().name(EVENT).data("{\"count\":" + unread + "}"));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping a closed notification stream of user {}", userId);
            remove(userId, emitter);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, mine) -> {
            mine.remove(emitter);
            return mine.isEmpty() ? null : mine;
        });
    }
}
