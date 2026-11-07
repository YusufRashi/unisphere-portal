package com.unisphere.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.support.AcademicIntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

class LiveNotificationsIT extends AcademicIntegrationTest {

    private static final Pattern UNREAD = Pattern.compile("event:unread\\s*\\ndata:\\{\"count\":(\\d+)}");

    @Autowired
    private NotificationCleanup cleanup;
    @Autowired
    private JdbcClient jdbc;

    /** The unread counts the stream has sent so far, in order. */
    private static java.util.List<Long> counts(MockHttpServletResponse response) throws Exception {
        Matcher m = UNREAD.matcher(response.getContentAsString());
        java.util.List<Long> found = new java.util.ArrayList<>();
        while (m.find()) {
            found.add(Long.parseLong(m.group(1)));
        }
        return found;
    }

    private static java.util.List<Long> waitForCounts(MockHttpServletResponse response, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        java.util.List<Long> found = counts(response);
        while (found.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            found = counts(response);
        }
        return found;
    }

    @Test
    void theStreamPushesTheUnreadCountWhenItChanges() throws Exception {
        Person lecturer = lecturer();
        long o = offering(course(3), lecturer, "MONDAY");
        Person student = student();
        register(student, o);

        MvcResult stream = mvc.perform(get("/api/notifications/stream").header(HttpHeaders.AUTHORIZATION, student.auth())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        MockHttpServletResponse response = stream.getResponse();
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        long before = waitForCounts(response, 1).getFirst();
        assertThat(before).isPositive(); // registration notifications are waiting

        call(postJson("/api/lecturer/offerings/" + o + "/announcements", Map.of("title", "Room change", "body", "LT2 from Monday."),
                lecturer.auth()), 201);
        assertThat(waitForCounts(response, 2)).containsExactly(before, before + 1);

        as(student.auth(), post("/api/notifications/read-all"), 204);
        assertThat(waitForCounts(response, 3)).endsWith(0L);
    }

    @Test
    void theStreamNeedsASignedInUser() throws Exception {
        mvc.perform(get("/api/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
    }

    @Test
    void cleanupDeletesOnlyNotificationsPastTheRetentionPeriod() {
        Long userId = jdbc.sql("select id from users order by id limit 1").query(Long.class).single();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String insert = "insert into notifications (user_id, type, title, message, created_at) values (:user, 'FEE_REMINDER', :title, 'm', :at)";
        jdbc.sql(insert).param("user", userId).param("title", "old-" + unique("N")).param("at", now.minus(Duration.ofDays(181))).update();
        String recent = "recent-" + unique("N");
        jdbc.sql(insert).param("user", userId).param("title", recent).param("at", now.minus(Duration.ofDays(179))).update();

        int deleted = cleanup.deleteExpired();

        assertThat(deleted).isPositive();
        assertThat(jdbc.sql("select count(*) from notifications where created_at < :cutoff")
                .param("cutoff", now.minus(Duration.ofDays(180))).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from notifications where title = :t").param("t", recent).query(Long.class).single()).isEqualTo(1);
    }
}
