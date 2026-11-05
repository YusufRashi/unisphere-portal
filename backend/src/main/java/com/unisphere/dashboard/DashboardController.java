package com.unisphere.dashboard;

import com.unisphere.dashboard.dto.DashboardDtos.AdminDashboard;
import com.unisphere.dashboard.dto.DashboardDtos.FinanceDashboard;
import com.unisphere.dashboard.dto.DashboardDtos.LecturerDashboard;
import com.unisphere.dashboard.dto.DashboardDtos.StudentDashboard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** One read-only summary per role, shown on the dashboard after signing in. */
@Tag(name = "Dashboards")
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final StudentDashboardService students;
    private final LecturerDashboardService lecturers;
    private final AdminDashboardService admins;
    private final FinanceDashboardService finance;

    @Operation(summary = "My CGPA and GPA trend, credit load, fee balance and upcoming exams")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/students/me/dashboard")
    public StudentDashboard student() {
        return students.mine();
    }

    @Operation(summary = "My courses this semester and the marks still to enter")
    @PreAuthorize("hasRole('LECTURER')")
    @GetMapping("/api/lecturer/dashboard")
    public LecturerDashboard lecturer() {
        return lecturers.mine();
    }

    @Operation(summary = "System-wide counts, registration activity and recently finalized results")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/dashboard")
    public AdminDashboard admin() {
        return admins.get();
    }

    @Operation(summary = "Collections: totals, outstanding balances, weekly payments and payment methods")
    @PreAuthorize("hasAnyRole('FINANCE', 'ADMIN')")
    @GetMapping("/api/fees/dashboard")
    public FinanceDashboard finance() {
        return finance.get();
    }
}
