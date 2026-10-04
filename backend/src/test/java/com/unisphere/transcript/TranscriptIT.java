package com.unisphere.transcript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.unisphere.support.AcademicIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

class TranscriptIT extends AcademicIntegrationTest {

    private MockHttpServletResponse pdf(String auth, String url) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk()).andReturn().getResponse();
    }

    @Test
    void aStudentGetsTheirTranscriptAsJsonAndPdfWithAVerifiableReference() throws Exception {
        Person lecturer = lecturer();
        long o1 = offering(course(3), lecturer, "MONDAY");
        long o2 = offering(course(3), lecturer, "TUESDAY");
        long o3 = offering(course(4), lecturer, "WEDNESDAY");
        Person student = student();
        register(student, o1, o2, o3);
        finalizeResult(lecturer, o1, student, "25", "55"); // 80 → A (4.0)
        finalizeResult(lecturer, o2, student, "15", "37"); // 52 → C (2.0)

        JsonNode transcript = as(student.auth(), get("/api/transcripts/me"), 200);
        assertThat(transcript.get("universityName").asString()).isEqualTo("UniSphere University");
        assertThat(transcript.get("title").asString()).isEqualTo("Unofficial Academic Transcript");
        assertThat(transcript.get("watermark").asString()).isEqualTo("UNOFFICIAL — NOT VALID WITHOUT REGISTRAR CERTIFICATION");
        assertThat(transcript.get("student").get("studentNumber").asString()).isEqualTo(student.number());
        assertThat(transcript.get("student").get("departmentName").asString()).startsWith("Dept ");
        assertThat(transcript.get("semesters")).hasSize(1);
        assertThat(transcript.get("semesters").get(0).get("courses")).hasSize(2);
        assertThat(transcript.get("cgpa").decimalValue()).isEqualByComparingTo("3.00"); // (3×4 + 3×2) / 6
        String reference = transcript.get("reference").asString();
        assertThat(reference).matches("^[0-9A-F]{4}(-[0-9A-F]{4}){3}$");

        // Re-issuing an unchanged record gives the same reference.
        assertThat(as(student.auth(), get("/api/transcripts/me"), 200).get("reference").asString()).isEqualTo(reference);

        MockHttpServletResponse response = pdf(student.auth(), "/api/transcripts/me/pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"transcript-" + student.number() + ".pdf\"");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        PdfReader reader = new PdfReader(response.getContentAsByteArray());
        assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains(student.number(), reference, "3.00");
        reader.close();

        JsonNode verified = as(admin, get("/api/transcripts/verify/" + reference.toLowerCase()), 200);
        assertThat(verified.get("studentNumber").asString()).isEqualTo(student.number());
        assertThat(verified.get("cgpa").decimalValue()).isEqualByComparingTo("3.00");
        assertThat(verified.get("resultsCount").asInt()).isEqualTo(2);
        assertThat(verified.get("issueCount").asInt()).isEqualTo(3);
        assertThat(verified.get("current").asBoolean()).isTrue();

        // A new finalized result changes the record: a new reference, and the old one is no longer current.
        finalizeResult(lecturer, o3, student, "30", "70");
        String newer = as(student.auth(), get("/api/transcripts/me"), 200).get("reference").asString();
        assertThat(newer).isNotEqualTo(reference);
        assertThat(as(admin, get("/api/transcripts/verify/" + reference), 200).get("current").asBoolean()).isFalse();
        assertThat(as(admin, get("/api/transcripts/verify/" + newer), 200).get("current").asBoolean()).isTrue();
    }

    @Test
    void onlyTheStudentAndTheRegistrarCanGetATranscript() throws Exception {
        Person student = student();
        Person classmate = student();
        Person lecturer = lecturer();

        as(classmate.auth(), get("/api/transcripts/students/" + student.id()), 403);
        as(classmate.auth(), get("/api/transcripts/students/" + student.id() + "/pdf"), 403);
        as(lecturer.auth(), get("/api/transcripts/me"), 403);
        as(lecturer.auth(), get("/api/transcripts/students/" + student.id()), 403);
        as(student.auth(), get("/api/transcripts/verify/0000-0000-0000-0000"), 403);
        call(get("/api/transcripts/me/pdf"), 401);

        // The registrar can issue any student's transcript, but has none of their own.
        assertThat(as(admin, get("/api/transcripts/students/" + student.id()), 200).get("student").get("studentNumber").asString())
                .isEqualTo(student.number());
        assertThat(pdf(admin, "/api/transcripts/students/" + student.id() + "/pdf").getContentType()).isEqualTo("application/pdf");
        as(admin, get("/api/transcripts/me"), 403);
        as(admin, get("/api/transcripts/students/987654321"), 404);
        assertThat(as(admin, get("/api/transcripts/verify/FFFF-FFFF-FFFF-FFFF"), 404).get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void aStudentWithoutResultsGetsAnEmptyTranscript() throws Exception {
        Person student = student();
        JsonNode transcript = as(student.auth(), get("/api/transcripts/me"), 200);
        assertThat(transcript.get("semesters")).isEmpty();
        assertThat(transcript.get("cgpa").isNull()).isTrue();
        assertThat(transcript.get("creditsEarned").asInt()).isZero();
        assertThat(as(admin, get("/api/transcripts/verify/" + transcript.get("reference").asString()), 200).get("resultsCount").asInt())
                .isZero();
    }
}
