package com.unisphere.result;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.offering.CourseOffering;
import com.unisphere.result.dto.ResultDtos.AcademicRecord;
import com.unisphere.result.dto.ResultDtos.CourseGrade;
import com.unisphere.result.dto.ResultDtos.SemesterRecord;
import com.unisphere.result.dto.ResultDtos.StudentInfo;
import com.unisphere.semester.Semester;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.student.Student;
import com.unisphere.student.StudentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's finalized results grouped by semester, with semester GPA and CGPA (RES-6…RES-8). Students
 * only ever see finalized results; marks still being worked on are invisible to them. Transcripts reuse
 * this record.
 */
@Service
@RequiredArgsConstructor
public class AcademicRecordService {

    private final StudentRepository students;
    private final CourseResultRepository results;

    @Transactional(readOnly = true)
    public AcademicRecord myRecord() {
        Student student = students.findByUserId(CurrentUser.id())
                .orElseThrow(() -> ApiException.forbidden("Only students have academic results."));
        return recordOf(student);
    }

    @Transactional(readOnly = true)
    public AcademicRecord recordFor(Long studentId) {
        return recordOf(students.findDetailedById(studentId).orElseThrow(() -> ApiException.notFound("Student", studentId)));
    }

    private AcademicRecord recordOf(Student student) {
        List<CourseResult> finalized = results.findFinalizedByStudentId(student.getId());
        List<GpaCalculator.Attempt> attempts = finalized.stream().map(AcademicRecordService::attempt).toList();
        Set<Long> counted = GpaCalculator.countedResultIds(attempts);

        // Newest semester first; courses by code within a semester.
        Map<Long, List<CourseResult>> bySemester = new LinkedHashMap<>();
        finalized.stream()
                .sorted(Comparator.comparing((CourseResult r) -> offering(r).getSemester().getStartDate()).reversed()
                        .thenComparing(r -> offering(r).getCourse().getCode()))
                .forEach(r -> bySemester.computeIfAbsent(offering(r).getSemester().getId(), id -> new ArrayList<>()).add(r));

        List<SemesterRecord> semesters = bySemester.values().stream().map(inSemester -> {
            Semester semester = offering(inSemester.getFirst()).getSemester();
            GpaCalculator.Summary summary = GpaCalculator.summarize(inSemester.stream().map(AcademicRecordService::attempt).toList());
            List<CourseGrade> courses = inSemester.stream().map(r -> grade(r, counted.contains(r.getId()))).toList();
            return new SemesterRecord(SemesterRef.from(semester), courses, summary.gpa(), summary.creditsAttempted(),
                    summary.creditsEarned());
        }).toList();

        GpaCalculator.Summary cumulative = GpaCalculator.cumulative(attempts);
        StudentInfo info = new StudentInfo(student.getId(), student.getStudentNumber(), student.getUser().getFullName(),
                student.getProgram().getCode(), student.getProgram().getName());
        return new AcademicRecord(info, semesters, cumulative.gpa(), cumulative.creditsAttempted(), cumulative.creditsEarned());
    }

    private static CourseOffering offering(CourseResult r) {
        return r.getRegistrationItem().getOffering();
    }

    private static GpaCalculator.Attempt attempt(CourseResult r) {
        CourseOffering o = offering(r);
        return new GpaCalculator.Attempt(r.getId(), o.getCourse().getId(), o.getSemester().getStartDate(),
                o.getCourse().getCredits(), r.getGradePoints(), r.getPassed());
    }

    private static CourseGrade grade(CourseResult r, boolean counts) {
        CourseOffering o = offering(r);
        return new CourseGrade(r.getId(), o.getCourse().getId(), o.getCourse().getCode(), o.getCourse().getTitle(),
                o.getCourse().getCredits(), o.getSection(), r.getCatMark(), r.getExamMark(), r.getTotalMark(), r.getGrade(),
                r.getGradePoints(), r.getPassed(), counts);
    }
}
