package com.unisphere.result;

import com.unisphere.common.web.PageResponse;
import com.unisphere.common.web.PageableFactory;
import com.unisphere.offering.ResultsStatus;
import com.unisphere.result.dto.ResultDtos.AcademicRecord;
import com.unisphere.result.dto.ResultDtos.GradingScaleRequest;
import com.unisphere.result.dto.ResultDtos.GradingScaleResponse;
import com.unisphere.result.dto.ResultDtos.MarksRequest;
import com.unisphere.result.dto.ResultDtos.MarksSheet;
import com.unisphere.result.dto.ResultDtos.ResultsQueueEntry;
import com.unisphere.result.dto.ResultDtos.ReturnRequest;
import com.unisphere.result.dto.ResultDtos.TeachingOffering;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Results & GPA")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResultsController {

    private static final Map<String, String> QUEUE_SORTS = Map.of("code", "course.code", "status", "resultsStatus",
            "submittedAt", "resultsSubmittedAt");

    private final LecturerResultsService lecturerService;
    private final ResultsAdminService adminService;
    private final AcademicRecordService recordService;
    private final GradingScaleService scaleService;

    // ---- Lecturer ----

    @Operation(summary = "My teaching assignments in a semester (default: current) with marks progress")
    @PreAuthorize("hasRole('LECTURER')")
    @GetMapping("/lecturer/offerings")
    public List<TeachingOffering> myOfferings(@RequestParam(required = false) Long semesterId) {
        return lecturerService.myOfferings(semesterId);
    }

    @Operation(summary = "Marks sheet: class list with marks and grades (own offering, or any for registrars)")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @GetMapping("/lecturer/offerings/{id}/results")
    public MarksSheet sheet(@PathVariable Long id) {
        return lecturerService.sheet(id);
    }

    @Operation(summary = "Save marks as a draft (own offering, results in DRAFT only)")
    @PreAuthorize("hasRole('LECTURER')")
    @PutMapping("/lecturer/offerings/{id}/results")
    public MarksSheet saveMarks(@PathVariable Long id, @Valid @RequestBody MarksRequest body) {
        return lecturerService.saveMarks(id, body.entries());
    }

    @Operation(summary = "Submit marks to the registrar (422 MARKS_INCOMPLETE if anyone is missing a mark)")
    @PreAuthorize("hasRole('LECTURER')")
    @PostMapping("/lecturer/offerings/{id}/results/submit")
    public MarksSheet submit(@PathVariable Long id) {
        return lecturerService.submit(id);
    }

    @Operation(summary = "Class statistics: mean, median, range, pass rate and grade distribution")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @GetMapping("/lecturer/offerings/{id}/statistics")
    public ResultStatistics.Statistics statistics(@PathVariable Long id) {
        return lecturerService.statistics(id);
    }

    // ---- Student ----

    @Operation(summary = "My finalized results by semester, with semester GPA and CGPA")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/results/me")
    public AcademicRecord myResults() {
        return recordService.myRecord();
    }

    // ---- Registrar ----

    @Operation(summary = "Results status of a semester's offerings (default: current semester)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/results")
    public PageResponse<ResultsQueueEntry> queue(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) ResultsStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageableFactory.of(page, size, sort, QUEUE_SORTS, Sort.by("course.code", "section", "id"));
        return adminService.search(semesterId, status, q, pageable);
    }

    @Operation(summary = "Finalize submitted results: grades are fixed and released to students")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/offerings/{id}/results/finalize")
    public MarksSheet finalizeResults(@PathVariable Long id) {
        return adminService.finalizeResults(id);
    }

    @Operation(summary = "Return submitted results to the lecturer with a comment")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/offerings/{id}/results/return")
    public MarksSheet returnResults(@PathVariable Long id, @Valid @RequestBody ReturnRequest body) {
        return adminService.returnToLecturer(id, body.comment());
    }

    @Operation(summary = "A student's finalized results with GPA and CGPA")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/students/{studentId}/results")
    public AcademicRecord studentResults(@PathVariable Long studentId) {
        return recordService.recordFor(studentId);
    }

    // ---- Grading scale ----

    @Operation(summary = "The active grading scale (bands highest first)")
    @GetMapping("/grading-scale")
    public GradingScaleResponse gradingScale() {
        return scaleService.get();
    }

    @Operation(summary = "Replace the grading scale's bands; finalized results keep their grades")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/grading-scale")
    public GradingScaleResponse replaceGradingScale(@Valid @RequestBody GradingScaleRequest body) {
        return scaleService.replace(body.bands());
    }
}
