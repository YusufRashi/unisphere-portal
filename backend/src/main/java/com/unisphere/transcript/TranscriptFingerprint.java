package com.unisphere.transcript;

import com.unisphere.result.dto.ResultDtos.CourseGrade;
import com.unisphere.result.dto.ResultDtos.SemesterRecord;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * A content fingerprint for a transcript: SHA-256 over a canonical text form of the student number and
 * every finalized grade. The verification reference is the first 64 bits, formatted {@code XXXX-XXXX-XXXX-XXXX}.
 * Generation time is deliberately excluded, so re-issuing an unchanged record gives the same reference.
 */
public final class TranscriptFingerprint {

    private TranscriptFingerprint() {
    }

    public record Fingerprint(String sha256, String reference) {
    }

    public static Fingerprint of(String studentNumber, List<SemesterRecord> semesters, BigDecimal cgpa) {
        StringBuilder canonical = new StringBuilder("transcript-v1|").append(studentNumber);
        for (SemesterRecord s : semesters) {
            canonical.append("|S:").append(s.semester().code());
            for (CourseGrade c : s.courses()) {
                canonical.append("|C:").append(c.resultId()).append(',').append(c.courseCode()).append(',')
                        .append(c.credits()).append(',').append(c.grade()).append(',').append(plain(c.gradePoints()))
                        .append(',').append(plain(c.totalMark()));
            }
        }
        canonical.append("|CGPA:").append(cgpa == null ? "-" : plain(cgpa));
        String hex = HexFormat.of().withUpperCase().formatHex(sha256(canonical.toString()));
        String reference = String.join("-", hex.substring(0, 4), hex.substring(4, 8), hex.substring(8, 12), hex.substring(12, 16));
        return new Fingerprint(hex.toLowerCase(), reference);
    }

    /** "3.50" and "3.5" must hash the same. */
    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
