package com.unisphere.result;

import com.unisphere.common.security.CurrentUser;
import com.unisphere.offering.CourseOffering;
import com.unisphere.offering.ResultsStatus;
import com.unisphere.registration.RegistrationItem;
import com.unisphere.registration.RegistrationItemRepository;
import com.unisphere.result.dto.ResultDtos.MarkRow;
import com.unisphere.result.dto.ResultDtos.MarksSheet;
import com.unisphere.result.dto.ResultDtos.SheetOffering;
import com.unisphere.result.dto.ResultDtos.SheetPermissions;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.student.Student;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Loads an offering's class list with marks and turns it into the marks sheet and statistics. */
@Component
@RequiredArgsConstructor
class ClassResults {

    private final RegistrationItemRepository items;
    private final CourseResultRepository results;
    private final GradingScaleService scale;

    /** A registered student and their result row, which does not exist until marks are first saved. */
    record Row(RegistrationItem item, CourseResult result) {

        boolean complete() {
            return result != null && result.isComplete();
        }
    }

    List<Row> load(Long offeringId) {
        Map<Long, CourseResult> byItem = results.findByOfferingId(offeringId).stream()
                .collect(Collectors.toMap(r -> r.getRegistrationItem().getId(), Function.identity()));
        return items.classList(offeringId).stream().map(i -> new Row(i, byItem.get(i.getId()))).toList();
    }

    static boolean taughtByCurrentUser(CourseOffering o) {
        return o.getLecturer() != null && o.getLecturer().getUser().getId().equals(CurrentUser.id());
    }

    MarksSheet sheet(CourseOffering o, List<Row> rows) {
        List<GradingRules.Band> bands = scale.activeBands();
        List<MarkRow> markRows = rows.stream().map(r -> markRow(r, bands)).toList();
        int complete = (int) rows.stream().filter(Row::complete).count();
        return new MarksSheet(sheetOffering(o), markRows, rows.size(), complete, permissions(o, rows.size(), complete));
    }

    ResultStatistics.Statistics statistics(List<Row> rows) {
        List<GradingRules.Band> bands = scale.activeBands();
        List<ResultStatistics.Graded> graded = rows.stream().filter(Row::complete).map(r -> {
            CourseResult result = r.result();
            if (result.isFinalized()) {
                return new ResultStatistics.Graded(result.getTotalMark(), result.getGrade(), result.getPassed());
            }
            GradingRules.Band band = GradingRules.gradeFor(result.getTotalMark(), bands);
            return new ResultStatistics.Graded(result.getTotalMark(), band.letter(), band.passing());
        }).toList();
        // Grades given under an earlier scale still appear, after the current letters.
        List<String> letters = Stream.concat(bands.stream().map(GradingRules.Band::letter),
                graded.stream().map(ResultStatistics.Graded::letter)).distinct().toList();
        return ResultStatistics.compute(rows.size(), graded, letters);
    }

    private static MarkRow markRow(Row row, List<GradingRules.Band> bands) {
        Student s = row.item().getRegistration().getStudent();
        CourseResult r = row.result();
        if (r == null) {
            return new MarkRow(row.item().getId(), s.getId(), s.getStudentNumber(), s.getUser().getFullName(),
                    null, null, null, null, null, null);
        }
        String grade = r.getGrade();
        BigDecimal points = r.getGradePoints();
        Boolean passed = r.getPassed();
        if (!r.isFinalized() && r.isComplete()) {
            GradingRules.Band preview = GradingRules.gradeFor(r.getTotalMark(), bands);
            grade = preview.letter();
            points = preview.gradePoints();
            passed = preview.passing();
        }
        return new MarkRow(row.item().getId(), s.getId(), s.getStudentNumber(), s.getUser().getFullName(),
                r.getCatMark(), r.getExamMark(), r.getTotalMark(), grade, points, passed);
    }

    private static SheetOffering sheetOffering(CourseOffering o) {
        return new SheetOffering(o.getId(), o.getCourse().getCode(), o.getCourse().getTitle(), o.getCourse().getCredits(),
                o.getSection(), SemesterRef.from(o.getSemester()),
                o.getLecturer() == null ? null : o.getLecturer().getDisplayName(), o.getCatWeight(), o.getExamWeight(),
                o.getResultsStatus(), o.getResultsSubmittedAt(), o.getResultsFinalizedAt(), o.getResultsReturnComment());
    }

    private static SheetPermissions permissions(CourseOffering o, int classSize, int complete) {
        boolean owner = taughtByCurrentUser(o);
        String locked = switch (o.getResultsStatus()) {
            case SUBMITTED -> "Marks have been submitted and are awaiting the registrar.";
            case FINALIZED -> "Results have been finalized and released to students.";
            case DRAFT -> owner ? null : "Only the lecturer teaching this course can enter marks.";
        };
        boolean canEdit = owner && o.getResultsStatus() == ResultsStatus.DRAFT;
        return new SheetPermissions(canEdit, canEdit && classSize > 0 && complete == classSize, locked);
    }
}
