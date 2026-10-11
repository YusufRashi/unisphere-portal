package com.unisphere.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisphere.support.AcademicIntegrationTest;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

class FinanceFlowIT extends AcademicIntegrationTest {

    private String finance;

    @BeforeEach
    void signInFinance() throws Exception {
        User user = createActiveUser(Role.FINANCE);
        finance = bearer(login(user.getEmail(), DEFAULT_PASSWORD));
    }

    // ---- Helpers ----

    /** A student registered (and approved) in the current semester. */
    private Person registeredStudent() throws Exception {
        Person lecturer = lecturer();
        long o = offering(course(3), lecturer, "MONDAY");
        Person student = student();
        register(student, o);
        return student;
    }

    private void setFee(String amount) throws Exception {
        call(putJson("/api/fees/structures", Map.of("programId", programId, "semesterId", semesterId, "amount", amount,
                "description", "Tuition"), finance), 200);
    }

    private JsonNode generate() throws Exception {
        return as(finance, post("/api/fees/semesters/" + semesterId + "/generate-charges"), 200);
    }

    private BigDecimal balance(JsonNode account) {
        return account.get("summary").get("balance").decimalValue();
    }

    private JsonNode recordPayment(Person student, String amount, String reference, int expectedStatus) throws Exception {
        return call(postJson("/api/fees/accounts/" + student.id() + "/payments",
                Map.of("amount", amount, "method", "BANK_TRANSFER", "reference", reference), finance), expectedStatus);
    }

    private JsonNode payOnline(Person student, String amount, String method, String key, int expectedStatus) throws Exception {
        return call(postJson("/api/fees/me/payments/simulate", Map.of("amount", amount, "method", method, "idempotencyKey", key),
                student.auth()), expectedStatus);
    }

    // ---- Tests ----

    @Test
    void billingIsIdempotentAndTheLedgerBalancesEveryCorrection() throws Exception {
        Person amina = registeredStudent();
        Person brian = registeredStudent();
        Person unregistered = student();
        setFee("85000.00");

        JsonNode preview = as(finance, get("/api/fees/semesters/" + semesterId + "/charges/preview"), 200);
        assertThat(preview.get("registeredStudents").asInt()).isEqualTo(2);
        assertThat(preview.get("toCharge").asInt()).isEqualTo(2);
        assertThat(preview.get("amountToCharge").decimalValue()).isEqualByComparingTo("170000.00");
        assertThat(preview.get("currency").asString()).isEqualTo("KES");

        JsonNode first = generate();
        assertThat(first.get("charged").asInt()).isEqualTo(2);
        JsonNode again = generate();
        assertThat(again.get("charged").asInt()).isZero();
        assertThat(again.get("alreadyCharged").asInt()).isEqualTo(2);

        // A billed fee cannot change amount (the description may).
        assertThat(call(putJson("/api/fees/structures", Map.of("programId", programId, "semesterId", semesterId, "amount", "90000.00",
                "description", "Tuition"), finance), 409).get("code").asString()).isEqualTo("INVALID_STATE");
        JsonNode structures = as(finance, get("/api/fees/structures").param("semesterId", String.valueOf(semesterId)), 200);
        JsonNode row = structures.get("rows").valueStream()
                .filter(r -> r.get("program").get("id").asLong() == programId).findFirst().orElseThrow();
        assertThat(row.get("chargedCount").asLong()).isEqualTo(2);

        JsonNode mine = as(amina.auth(), get("/api/fees/me"), 200);
        assertThat(balance(mine)).isEqualByComparingTo("85000.00");
        assertThat(mine.get("charges").get(0).get("description").asString()).isEqualTo("Tuition — " + semesterName);
        assertThat(balance(as(unregistered.auth(), get("/api/fees/me"), 200))).isEqualByComparingTo("0.00");

        // Office payment, then the same receipt again (any case) is refused.
        String receipt = unique("RCPT-");
        assertThat(balance(recordPayment(amina, "40000.00", receipt, 200))).isEqualByComparingTo("45000.00");
        JsonNode duplicate = recordPayment(amina, "40000.00", receipt.toLowerCase(), 409);
        assertThat(duplicate.get("code").asString()).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(duplicate.get("errors").get(0).get("field").asString()).isEqualTo("reference");
        Map<String, Object> future = new HashMap<>(Map.of("amount", "10.00", "method", "CASH", "reference", unique("R-")));
        future.put("paidAt", clock.instant().plusSeconds(3600).toString());
        assertThat(call(postJson("/api/fees/accounts/" + amina.id() + "/payments", future, finance), 400)
                .get("errors").get(0).get("field").asString()).isEqualTo("paidAt");

        // Manual charge, then voided; payment reversed. Nothing is deleted.
        JsonNode charged = call(postJson("/api/fees/accounts/" + amina.id() + "/charges",
                Map.of("description", "Library fine", "amount", "1500.00"), finance), 200);
        assertThat(balance(charged)).isEqualByComparingTo("46500.00");
        long fine = charged.get("charges").get(0).get("id").asLong();
        JsonNode voided = call(postJson("/api/fees/charges/" + fine + "/void", Map.of("reason", "Book returned"), finance), 200);
        assertThat(balance(voided)).isEqualByComparingTo("45000.00");
        assertThat(voided.get("charges")).hasSize(2);
        call(postJson("/api/fees/charges/" + fine + "/void", Map.of("reason", "Again"), finance), 409);

        long paymentId = voided.get("payments").get(0).get("id").asLong();
        JsonNode reversed = call(postJson("/api/fees/payments/" + paymentId + "/reverse", Map.of("reason", "Cheque bounced"), finance), 200);
        assertThat(balance(reversed)).isEqualByComparingTo("85000.00");
        assertThat(reversed.get("payments").get(0).get("status").asString()).isEqualTo("REVERSED");
        call(postJson("/api/fees/payments/" + paymentId + "/reverse", Map.of("reason", "Again"), finance), 409);

        // The SQL list agrees with the statement, and filters by balance.
        JsonNode owing = as(finance, get("/api/fees/accounts").param("programId", String.valueOf(programId))
                .param("hasBalance", "true").param("sort", "balance,desc"), 200);
        assertThat(owing.get("totalElements").asInt()).isEqualTo(2);
        JsonNode aminaRow = owing.get("content").valueStream()
                .filter(r -> r.get("studentId").asLong() == amina.id()).findFirst().orElseThrow();
        assertThat(aminaRow.get("balance").decimalValue()).isEqualByComparingTo("85000.00");
        assertThat(as(finance, get("/api/fees/accounts").param("programId", String.valueOf(programId)).param("hasBalance", "false"), 200)
                .get("content").valueStream().map(r -> r.get("studentId").asLong())).containsExactly(unregistered.id());

        // Reports: CSV for spreadsheets, payments by date.
        MockHttpServletResponse csv = mvc.perform(get("/api/fees/reports/outstanding").param("programId", String.valueOf(programId))
                        .param("format", "csv").header(HttpHeaders.AUTHORIZATION, finance))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(csv.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(csv.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment; filename=\"outstanding-balances-");
        String body = csv.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).startsWith("﻿Student number,Name,Email,Program,Currency,Charged,Paid,Balance\r\n");
        assertThat(body).contains(amina.number(), brian.number()).doesNotContain(unregistered.number());

        String today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
        JsonNode report = as(finance, get("/api/fees/reports/payments").param("from", today).param("to", today), 200);
        JsonNode reported = report.get("rows").valueStream()
                .filter(r -> r.get("reference").asString().equals(receipt)).findFirst().orElseThrow();
        assertThat(reported.get("status").asString()).isEqualTo("REVERSED");
        as(finance, get("/api/fees/reports/payments").param("from", today).param("to", "2000-01-01"), 400);
    }

    @Test
    void onlinePaymentsAreRepeatSafeAndNeverOverpay() throws Exception {
        Person student = registeredStudent();
        setFee("85000.00");
        generate();

        assertThat(payOnline(student, "85000.01", "MOBILE_MONEY", UUID.randomUUID().toString(), 422).get("detail").asString())
                .isEqualTo("You can pay at most your outstanding balance of 85000.00.");
        payOnline(student, "100.00", "CASH", UUID.randomUUID().toString(), 422);

        String key = UUID.randomUUID().toString();
        JsonNode paid = payOnline(student, "30000.00", "MOBILE_MONEY", key, 200);
        assertThat(paid.get("payment").get("status").asString()).isEqualTo("COMPLETED");
        assertThat(paid.get("payment").get("reference").asString()).matches("^UNI-\\d{8}-[0-9A-F]{8}$");
        assertThat(balance(paid.get("account"))).isEqualByComparingTo("55000.00");
        // Retrying with the same key (a double click, a timeout) returns the same payment instead of paying twice.
        JsonNode retry = payOnline(student, "30000.00", "MOBILE_MONEY", key, 200);
        assertThat(retry.get("payment").get("id").asLong()).isEqualTo(paid.get("payment").get("id").asLong());
        assertThat(balance(retry.get("account"))).isEqualByComparingTo("55000.00");

        JsonNode declined = payOnline(student, "100.13", "CARD", UUID.randomUUID().toString(), 200);
        assertThat(declined.get("payment").get("status").asString()).isEqualTo("FAILED");
        assertThat(declined.get("payment").get("failureReason").asString()).contains("No money was taken");
        assertThat(balance(declined.get("account"))).isEqualByComparingTo("55000.00");

        // Two simultaneous submissions with one key: exactly one payment.
        String concurrentKey = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<JsonNode> task = () -> {
                start.await();
                return payOnline(student, "5000.00", "CARD", concurrentKey, 200);
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        long id1 = results.get(0).get().get("payment").get("id").asLong();
        long id2 = results.get(1).get().get("payment").get("id").asLong();
        pool.shutdown();
        assertThat(id1).isEqualTo(id2);
        JsonNode account = as(student.auth(), get("/api/fees/me"), 200);
        assertThat(balance(account)).isEqualByComparingTo("50000.00");
        assertThat(account.get("payments")).hasSize(3);
    }

    @Test
    void eachRoleReachesOnlyItsOwnFinanceFunctions() throws Exception {
        Person student = registeredStudent();
        Person other = student();
        Person lecturer = lecturer();

        // Students: own account only.
        as(student.auth(), get("/api/fees/accounts"), 403);
        as(student.auth(), get("/api/fees/accounts/" + other.id()), 403);
        call(postJson("/api/fees/accounts/" + student.id() + "/payments",
                Map.of("amount", "1.00", "method", "CASH", "reference", unique("R-")), student.auth()), 403);
        as(student.auth(), get("/api/fees/reports/outstanding"), 403);

        // Registrar: read-only.
        as(admin, get("/api/fees/accounts"), 200);
        as(admin, get("/api/fees/accounts/" + student.id()), 200);
        as(admin, get("/api/fees/reports/outstanding"), 200);
        call(postJson("/api/fees/accounts/" + student.id() + "/charges", Map.of("description", "x", "amount", "1.00"), admin), 403);
        as(admin, post("/api/fees/semesters/" + semesterId + "/generate-charges"), 403);
        as(admin, get("/api/fees/me"), 403);

        // Neither lecturers nor finance staff have a fee account of their own.
        as(lecturer.auth(), get("/api/fees/me"), 403);
        as(lecturer.auth(), get("/api/fees/accounts"), 403);
        as(finance, get("/api/fees/me"), 403);
        payOnline(lecturer, "1.00", "CARD", UUID.randomUUID().toString(), 403);

        call(get("/api/fees/me"), 401);
        assertThat(as(finance, get("/api/fees/accounts/987654321"), 404).get("code").asString()).isEqualTo("NOT_FOUND");
    }
}
