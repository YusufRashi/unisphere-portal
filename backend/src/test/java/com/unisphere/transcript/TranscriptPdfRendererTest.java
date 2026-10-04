package com.unisphere.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.unisphere.result.dto.ResultDtos.CourseGrade;
import com.unisphere.result.dto.ResultDtos.SemesterRecord;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.transcript.dto.TranscriptDtos.Transcript;
import com.unisphere.transcript.dto.TranscriptDtos.TranscriptStudent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TranscriptPdfRendererTest {

    private static final String REFERENCE = "1A2B-3C4D-5E6F-7A8B";

    private final TranscriptPdfRenderer renderer = new TranscriptPdfRenderer();

    private static Transcript transcript(int semesters, int coursesPerSemester) {
        List<SemesterRecord> records = new ArrayList<>();
        for (int s = 1; s <= semesters; s++) {
            int semester = s;
            List<CourseGrade> courses = IntStream.rangeClosed(1, coursesPerSemester)
                    .mapToObj(c -> new CourseGrade((long) semester * 100 + c, (long) c, "C%d%02d".formatted(semester, c),
                            "Course %d of semester %d".formatted(c, semester), 3, "A", new BigDecimal("20"), new BigDecimal("45"),
                            new BigDecimal("65"), c == 1 ? "F" : "B+", c == 1 ? BigDecimal.ZERO : new BigDecimal("3.50"), c != 1,
                            !(semester == 1 && c == 1)))
                    .toList();
            records.add(new SemesterRecord(new SemesterRef((long) s, "2026-S" + s, "Semester " + s + ", 2026/2027"), courses,
                    new BigDecimal("3.27"), coursesPerSemester * 3, (coursesPerSemester - 1) * 3));
        }
        TranscriptStudent student = new TranscriptStudent(4L, "S2026-00004", "Amina Otieno", "BSC-CS", "BSc Computer Science",
                "BACHELOR", "Computer Science", LocalDate.of(2026, 9, 1), 2, "ACTIVE");
        return new Transcript("UniSphere University", TranscriptService.TITLE, student, records, new BigDecimal("3.41"), 30, 27,
                Instant.parse("2026-10-04T16:10:00Z"), REFERENCE, TranscriptService.WATERMARK);
    }

    @Test
    void producesAPdfWithTheTranscriptContent() throws Exception {
        byte[] pdf = renderer.render(transcript(2, 4));
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        PdfReader reader = new PdfReader(pdf);
        String first = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(first).contains("UniSphere University", "Unofficial Academic Transcript", "Amina Otieno", "S2026-00004",
                "BSC-CS", "Computer Science", "Semester 1, 2026/2027", "C101", "Course 2 of semester 1", "3.41");
        reader.close();
    }

    @Test
    void everyPageCarriesTheWatermarkTimestampAndReference() throws Exception {
        PdfReader reader = new PdfReader(renderer.render(transcript(4, 12)));
        assertThat(reader.getNumberOfPages()).isGreaterThan(1);

        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            String text = extractor.getTextFromPage(page);
            assertThat(text).as("page %d", page)
                    .contains("NOT VALID WITHOUT REGISTRAR CERTIFICATION", "Generated 4 Oct 2026, 16:10 UTC", REFERENCE, "Page " + page);
            // The diagonal watermark and the footer line: the text is drawn twice on every page.
            String content = new String(reader.getPageContent(page), StandardCharsets.ISO_8859_1);
            assertThat(content.split("NOT VALID WITHOUT REGISTRAR CERTIFICATION", -1)).as("page %d", page).hasSize(3);
        }
        reader.close();
    }

    @Test
    void marksRepeatedAttemptsAndHandlesAnEmptyRecord() throws Exception {
        PdfReader reader = new PdfReader(renderer.render(transcript(1, 3)));
        assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("C101 *", "excluded from the CGPA");
        reader.close();

        PdfReader empty = new PdfReader(renderer.render(transcript(0, 0)));
        assertThat(new PdfTextExtractor(empty).getTextFromPage(1)).contains("No finalized results yet.");
        empty.close();
    }
}
