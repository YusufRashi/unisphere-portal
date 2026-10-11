package com.unisphere.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.unisphere.support.AcademicIntegrationTest;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class NotificationFlowIT extends AcademicIntegrationTest {

    @Autowired
    private ReminderService reminders;

    // ---- Helpers ----

    /** Every notification of the user, newest first, across all pages. */
    private List<JsonNode> inbox(Person p) throws Exception {
        List<JsonNode> all = new ArrayList<>();
        JsonNode page;
        int number = 0;
        do {
            page = as(p.auth(), get("/api/notifications").param("size", "100").param("page", String.valueOf(number++)), 200);
            page.get("content").forEach(all::add);
        } while (number < page.get("totalPages").asInt());
        return all;
    }

    private List<JsonNode> ofType(Person p, String type) throws Exception {
        return inbox(p).stream().filter(n -> n.get("type").asString().equals(type)).toList();
    }

    /** Notifications about this test's semester only: other tests leave semesters open in the shared database. */
    private List<JsonNode> aboutThisSemester(Person p, String type) throws Exception {
        return ofType(p, type).stream().filter(n -> n.get("title").asString().contains(semesterName)).toList();
    }

    private long unread(Person p) throws Exception {
        return as(p.auth(), get("/api/notifications/unread-count"), 200).get("count").asLong();
    }

    private void addCourse(Person student, long offeringId) throws Exception {
        call(postJson("/api/registrations/me/current/items", Map.of("offeringId", offeringId), student.auth()), 200);
    }

    /** Runs the reminder job as if it were {@code later}, then puts the clock back so access tokens stay valid. */
    private void runRemindersAt(Instant later) {
        Instant now = clock.instant();
        clock.set(later);
        try {
            reminders.sendDueReminders();
        } finally {
            clock.set(now);
        }
    }

    // ---- Tests ----

    @Test
    void registrationEventsNotifyStudentsOnce() throws Exception {
        Person student = student();
        openSemester(true);
        assertThat(ofType(student, "REGISTRATION_OPENED")).singleElement()
                .satisfies(n -> {
                    assertThat(n.get("title").asString()).isEqualTo("Registration is open for " + semesterName);
                    assertThat(n.get("link").asString()).isEqualTo("/student/registration");
                    assertThat(n.get("read").asBoolean()).isFalse();
                });
        // Closing and re-opening does not notify everyone again.
        as(admin, post("/api/admin/semesters/" + semesterId + "/registration/close"), 200);
        as(admin, post("/api/admin/semesters/" + semesterId + "/registration/open"), 200);
        assertThat(ofType(student, "REGISTRATION_OPENED")).hasSize(1);

        addCourse(student, offering(course(3), lecturer(), "MONDAY"));
        long registrationId = as(student.auth(), post("/api/registrations/me/current/submit"), 200).get("id").asLong();
        assertThat(ofType(student, "REGISTRATION_SUBMITTED")).hasSize(1);

        call(postJson("/api/admin/registrations/" + registrationId + "/reject", Map.of("reason", "Add a second course"), admin), 200);
        assertThat(ofType(student, "REGISTRATION_REJECTED")).singleElement()
                .satisfies(n -> assertThat(n.get("message").asString()).contains("was not approved: Add a second course."));

        as(student.auth(), post("/api/registrations/me/current/submit"), 200);
        as(admin, post("/api/admin/registrations/" + registrationId + "/approve"), 200);
        assertThat(ofType(student, "REGISTRATION_APPROVED")).singleElement()
                .satisfies(n -> assertThat(n.get("link").asString()).isEqualTo("/student/timetable"));
        assertThat(ofType(student, "REGISTRATION_SUBMITTED")).hasSize(2);
    }

    @Test
    void finalizedResultsNotifyTheClassOnly() throws Exception {
        Person lecturer = lecturer();
        long o = offering(course(3), lecturer, "MONDAY");
        Person inClass = student();
        Person notInClass = student();
        register(inClass, o);

        finalizeResult(lecturer, o, inClass, "20", "50");

        assertThat(ofType(inClass, "RESULTS_RELEASED")).singleElement()
                .satisfies(n -> assertThat(n.get("message").asString()).contains("(" + semesterName + ") is now available"));
        assertThat(ofType(notInClass, "RESULTS_RELEASED")).isEmpty();
    }

    @Test
    void usersReadOnlyTheirOwnInbox() throws Exception {
        Person student = student();
        Person other = student();
        openSemester();
        addCourse(student, offering(course(3), lecturer(), "MONDAY"));
        as(student.auth(), post("/api/registrations/me/current/submit"), 200);
        assertThat(unread(student)).isEqualTo(2);

        JsonNode newest = inbox(student).getFirst();
        assertThat(newest.get("type").asString()).isEqualTo("REGISTRATION_APPROVED");
        long id = newest.get("id").asLong();

        // Someone else's notification does not exist as far as this user can tell.
        as(other.auth(), post("/api/notifications/" + id + "/read"), 404);
        as(student.auth(), post("/api/notifications/" + id + "/read"), 204);
        as(student.auth(), post("/api/notifications/" + id + "/read"), 204);
        assertThat(unread(student)).isEqualTo(1);
        JsonNode unreadOnly = as(student.auth(), get("/api/notifications").param("unreadOnly", "true"), 200);
        assertThat(unreadOnly.get("totalElements").asLong()).isEqualTo(1);
        assertThat(unreadOnly.get("content").get(0).get("type").asString()).isEqualTo("REGISTRATION_OPENED");

        as(student.auth(), post("/api/notifications/read-all"), 204);
        assertThat(unread(student)).isZero();
        assertThat(unread(other)).isEqualTo(1);

        call(get("/api/notifications"), 401);
        as(student.auth(), get("/api/notifications").param("size", "101"), 400);
    }

    @Test
    void lecturersAnnounceToTheirClass() throws Exception {
        Person lecturer = lecturer();
        Person otherLecturer = lecturer();
        long o = offering(course(3), lecturer, "MONDAY");
        long otherOffering = offering(course(3), otherLecturer, "TUESDAY");
        Person enrolled = student();
        Person elsewhere = student();
        register(enrolled, o);
        register(elsewhere, otherOffering);
        String url = "/api/lecturer/offerings/" + o + "/announcements";

        call(postJson(url, Map.of("title", " ", "body", "Text"), lecturer.auth()), 400);
        call(postJson(url, Map.of("title", "Hi", "body", "Text"), otherLecturer.auth()), 403);
        call(postJson(url, Map.of("title", "Hi", "body", "Text"), enrolled.auth()), 403);
        call(postJson("/api/lecturer/offerings/999999999/announcements", Map.of("title", "Hi", "body", "Text"), lecturer.auth()), 404);

        String body = "Bring a calculator. ".repeat(30).strip();
        JsonNode published = call(postJson(url, Map.of("title", "Room change", "body", body), lecturer.auth()), 201);
        assertThat(published.get("notified").asInt()).isEqualTo(1);
        assertThat(published.get("announcement").get("authorName").asString()).isEqualTo("Test Person");

        JsonNode notification = ofType(enrolled, "ANNOUNCEMENT").getFirst();
        assertThat(notification.get("title").asString()).endsWith(": Room change");
        assertThat(notification.get("message").asString()).hasSize(AnnouncementService.PREVIEW_LENGTH).endsWith("…");
        assertThat(notification.get("link").asString()).isEqualTo("/announcements?offeringId=" + o);
        assertThat(ofType(elsewhere, "ANNOUNCEMENT")).isEmpty();

        // The registrar may post to any offering.
        call(postJson(url, Map.of("title", "From the registrar", "body", "Exams move to week 14."), admin), 201);

        JsonNode seen = as(enrolled.auth(), get("/api/announcements"), 200);
        assertThat(seen.get("content").valueStream().map(a -> a.get("title").asString()))
                .containsExactly("From the registrar", "Room change");
        assertThat(seen.get("content").get(1).get("body").asString()).isEqualTo(body);
        assertThat(as(lecturer.auth(), get("/api/announcements").param("offeringId", String.valueOf(o)), 200)
                .get("totalElements").asLong()).isEqualTo(2);
        assertThat(as(elsewhere.auth(), get("/api/announcements"), 200).get("totalElements").asLong()).isZero();
        assertThat(as(otherLecturer.auth(), get("/api/announcements"), 200).get("totalElements").asLong()).isZero();

        User finance = createActiveUser(Role.FINANCE);
        as(bearer(login(finance.getEmail(), DEFAULT_PASSWORD)), get("/api/announcements"), 403);
    }

    @Test
    void registrationDeadlineReminderGoesToUnregisteredStudentsOnce() throws Exception {
        long o = offering(course(3), lecturer(), "MONDAY");
        Person registered = student();
        Person unregistered = student();
        register(registered, o);

        // Registration closes 7 days after "now"; 3 days later it is not due yet, 6 days later it is.
        runRemindersAt(clock.instant().plus(Duration.ofDays(3)));
        assertThat(aboutThisSemester(unregistered, "REGISTRATION_DEADLINE")).isEmpty();

        runRemindersAt(clock.instant().plus(Duration.ofDays(6)));
        runRemindersAt(clock.instant().plus(Duration.ofDays(6)).plusSeconds(3600));
        assertThat(aboutThisSemester(unregistered, "REGISTRATION_DEADLINE")).singleElement()
                .satisfies(n -> assertThat(n.get("title").asString()).isEqualTo("Registration for " + semesterName + " closes soon"));
        assertThat(aboutThisSemester(registered, "REGISTRATION_DEADLINE")).isEmpty();
    }

    @Test
    void feeReminderGoesToStudentsWhoOweOncePerInterval() throws Exception {
        User financeUser = createActiveUser(Role.FINANCE);
        String finance = bearer(login(financeUser.getEmail(), DEFAULT_PASSWORD));
        Person owing = student();
        Person settled = student();
        call(postJson("/api/fees/accounts/" + owing.id() + "/charges", Map.of("description", "Library fine", "amount", "1250.00"),
                finance), 200);

        runRemindersAt(clock.instant());
        runRemindersAt(clock.instant());

        assertThat(ofType(owing, "FEE_REMINDER")).singleElement()
                .satisfies(n -> assertThat(n.get("message").asString()).startsWith("Your fee balance is KES 1,250.00."));
        assertThat(ofType(settled, "FEE_REMINDER")).isEmpty();

        // A new interval brings a new reminder.
        runRemindersAt(clock.instant().plus(Duration.ofDays(15)));
        assertThat(ofType(owing, "FEE_REMINDER")).hasSize(2);
    }
}
