package com.unisphere.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import com.unisphere.result.dto.ResultDtos.CourseGrade;
import com.unisphere.result.dto.ResultDtos.SemesterRecord;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptFingerprintTest {

    static CourseGrade grade(long resultId, String code, String letter, String points, String total) {
        return new CourseGrade(resultId, resultId * 10, code, code + " title", 3, "A", new BigDecimal("20"), new BigDecimal("50"),
                new BigDecimal(total), letter, new BigDecimal(points), !letter.equals("F"), true);
    }

    static List<SemesterRecord> record(CourseGrade... grades) {
        return List.of(new SemesterRecord(new SemesterRef(1L, "2026-S1", "Semester 1"), List.of(grades), new BigDecimal("3.50"), 6, 6));
    }

    @Test
    void sameContentGivesTheSameFormattedReference() {
        var a = TranscriptFingerprint.of("S2026-00004", record(grade(1, "CS101", "A", "4.00", "82")), new BigDecimal("4.00"));
        var b = TranscriptFingerprint.of("S2026-00004", record(grade(1, "CS101", "A", "4.0", "82.00")), new BigDecimal("4"));
        assertThat(a).isEqualTo(b);
        assertThat(a.reference()).matches("^[0-9A-F]{4}(-[0-9A-F]{4}){3}$");
        assertThat(a.sha256()).hasSize(64).startsWith(a.reference().replace("-", "").toLowerCase());
    }

    @Test
    void anyChangeToTheRecordChangesTheReference() {
        String base = TranscriptFingerprint.of("S2026-00004", record(grade(1, "CS101", "A", "4.00", "82")), new BigDecimal("4.00")).reference();
        assertThat(TranscriptFingerprint.of("S2026-00005", record(grade(1, "CS101", "A", "4.00", "82")), new BigDecimal("4.00")).reference())
                .isNotEqualTo(base);
        assertThat(TranscriptFingerprint.of("S2026-00004", record(grade(1, "CS101", "B+", "3.50", "68")), new BigDecimal("3.50")).reference())
                .isNotEqualTo(base);
        assertThat(TranscriptFingerprint.of("S2026-00004",
                record(grade(1, "CS101", "A", "4.00", "82"), grade(2, "MATH110", "C", "2.00", "52")), new BigDecimal("3.00")).reference())
                .isNotEqualTo(base);
    }

    @Test
    void anEmptyRecordStillHasAReference() {
        assertThat(TranscriptFingerprint.of("S2026-00009", List.of(), null).reference()).hasSize(19);
    }
}
