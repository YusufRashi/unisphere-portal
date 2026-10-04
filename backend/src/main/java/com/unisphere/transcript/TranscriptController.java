package com.unisphere.transcript;

import com.unisphere.transcript.dto.TranscriptDtos.Transcript;
import com.unisphere.transcript.dto.TranscriptDtos.Verification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transcripts")
@RestController
@RequestMapping("/api/transcripts")
@RequiredArgsConstructor
public class TranscriptController {

    private final TranscriptService service;
    private final TranscriptPdfRenderer renderer;

    @Operation(summary = "My unofficial transcript")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public Transcript mine() {
        return service.issueMine("json");
    }

    @Operation(summary = "My unofficial transcript as a PDF")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me/pdf")
    public ResponseEntity<byte[]> minePdf() {
        return pdf(service.issueMine("pdf"));
    }

    @Operation(summary = "A student's unofficial transcript")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/students/{studentId}")
    public Transcript forStudent(@PathVariable Long studentId) {
        return service.issueFor(studentId, "json");
    }

    @Operation(summary = "A student's unofficial transcript as a PDF")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/students/{studentId}/pdf")
    public ResponseEntity<byte[]> forStudentPdf(@PathVariable Long studentId) {
        return pdf(service.issueFor(studentId, "pdf"));
    }

    @Operation(summary = "Check a verification reference printed on a transcript")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/verify/{reference}")
    public Verification verify(@PathVariable String reference) {
        return service.verify(reference);
    }

    private ResponseEntity<byte[]> pdf(Transcript transcript) {
        String filename = "transcript-" + transcript.student().studentNumber() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                // Personal data: never keep a copy in shared or browser caches.
                .cacheControl(CacheControl.noStore())
                .body(renderer.render(transcript));
    }
}
