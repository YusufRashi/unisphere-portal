package com.unisphere.notification;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates notifications. Sending to a group is a single {@code INSERT … SELECT}, so notifying every student of
 * the university costs one statement rather than one per student.
 */
@Component
@RequiredArgsConstructor
public class NotificationWriter {

    static final int TITLE_MAX = 200;

    private static final String INSERT = """
            insert into notifications (user_id, type, title, message, link, dedupe_key, created_at)
            select r.user_id, cast(:type as varchar), cast(:title as varchar), cast(:message as text), cast(:link as varchar),
                   cast(:dedupeKey as varchar), cast(:now as timestamptz)
            from (%s) r
            on conflict (user_id, dedupe_key) where dedupe_key is not null do nothing
            returning user_id""";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final NotificationStream stream;

    /** @param link a path inside the web app such as {@code /student/results}, or null */
    public record Message(NotificationType type, String title, String message, String link) {

        public Message {
            title = abbreviate(title, TITLE_MAX);
        }
    }

    /** @return 1 if sent, 0 if the user already has a notification with this {@code dedupeKey} */
    @Transactional
    public int send(Long userId, Message message, String dedupeKey) {
        return sendTo("select cast(:userId as bigint) as user_id", Map.of("userId", userId), message, dedupeKey);
    }

    /**
     * @param recipients SQL returning a {@code user_id} column (see {@link Recipients}), using {@code params}
     * @param dedupeKey  when set, users who already received a notification with this key are skipped
     * @return how many notifications were created
     */
    @Transactional
    public int sendTo(String recipients, Map<String, ?> params, Message message, String dedupeKey) {
        Map<String, Object> all = new HashMap<>(params);
        all.put("type", message.type().name());
        all.put("title", message.title());
        all.put("message", message.message());
        all.put("link", message.link());
        all.put("dedupeKey", dedupeKey);
        all.put("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        List<Long> recipientsNotified = jdbc.sql(INSERT.formatted(recipients)).params(all).query(Long.class).list();
        stream.changed(recipientsNotified);
        return recipientsNotified.size();
    }

    static String abbreviate(String text, int max) {
        String trimmed = text.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1).stripTrailing() + "…";
    }
}
