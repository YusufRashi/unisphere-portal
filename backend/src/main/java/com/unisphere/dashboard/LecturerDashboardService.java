package com.unisphere.dashboard;

import com.unisphere.dashboard.dto.DashboardDtos.CourseProgress;
import com.unisphere.dashboard.dto.DashboardDtos.LecturerDashboard;
import com.unisphere.offering.ResultsStatus;
import com.unisphere.offering.dto.OfferingDtos.OfferingResponse;
import com.unisphere.result.LecturerResultsService;
import com.unisphere.result.dto.ResultDtos.TeachingOffering;
import com.unisphere.semester.SemesterRepository;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in lecturer's teaching this semester, and what still needs doing. */
@Service
@RequiredArgsConstructor
public class LecturerDashboardService {

    private final LecturerResultsService teaching;
    private final SemesterRepository semesters;

    @Transactional(readOnly = true)
    public LecturerDashboard mine() {
        List<TeachingOffering> mine = teaching.myOfferings(null);
        int students = mine.stream().mapToInt(TeachingOffering::classSize).sum();
        int marksMissing = mine.stream()
                .filter(t -> t.offering().resultsStatus() == ResultsStatus.DRAFT)
                .mapToInt(t -> t.classSize() - t.marksComplete())
                .sum();
        int awaitingReview = (int) mine.stream().filter(t -> t.offering().resultsStatus() == ResultsStatus.SUBMITTED).count();
        return new LecturerDashboard(semesters.findByCurrentTrue().map(SemesterRef::from).orElse(null), mine.size(), students, marksMissing,
                awaitingReview, mine.stream().map(LecturerDashboardService::progress).toList());
    }

    private static CourseProgress progress(TeachingOffering t) {
        OfferingResponse o = t.offering();
        return new CourseProgress(o.id(), o.course().code(), o.course().title(), o.section(), o.resultsStatus(), t.classSize(), t.marksComplete());
    }
}
