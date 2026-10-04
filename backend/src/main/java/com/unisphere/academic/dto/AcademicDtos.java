package com.unisphere.academic.dto;

import com.unisphere.academic.AwardLevel;
import com.unisphere.academic.Course;
import com.unisphere.academic.Department;
import com.unisphere.academic.Program;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Request/response bodies for departments, programs and courses. */
public final class AcademicDtos {

    private AcademicDtos() {
    }

    static final String CODE_PATTERN = "^[A-Za-z0-9][A-Za-z0-9-]*$";
    static final String CODE_MESSAGE = "may contain only letters, digits and hyphens";

    // ---- References embedded in other responses -------------------------------------------------

    public record DepartmentRef(Long id, String code, String name) {
        public static DepartmentRef from(Department d) {
            return new DepartmentRef(d.getId(), d.getCode(), d.getName());
        }
    }

    public record ProgramRef(Long id, String code, String name) {
        public static ProgramRef from(Program p) {
            return new ProgramRef(p.getId(), p.getCode(), p.getName());
        }
    }

    public record CourseRef(Long id, String code, String title, int credits) {
        public static CourseRef from(Course c) {
            return new CourseRef(c.getId(), c.getCode(), c.getTitle(), c.getCredits());
        }
    }

    // ---- Departments ------------------------------------------------------------------------------

    public record DepartmentRequest(
            @NotBlank @Size(max = 10) @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 150) String faculty) {
    }

    public record DepartmentResponse(
            Long id, String code, String name, String faculty, long programCount, long courseCount, long lecturerCount) {
    }

    // ---- Programs ---------------------------------------------------------------------------------

    public record ProgramRequest(
            @NotNull Long departmentId,
            @NotBlank @Size(max = 20) @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull AwardLevel awardLevel,
            @NotNull @Min(1) @Max(8) Integer durationYears,
            Boolean active) {
    }

    public record ProgramResponse(
            Long id, String code, String name, AwardLevel awardLevel, int durationYears, boolean active,
            DepartmentRef department, long studentCount) {

        public static ProgramResponse from(Program p, long studentCount) {
            return new ProgramResponse(p.getId(), p.getCode(), p.getName(), p.getAwardLevel(), p.getDurationYears(),
                    p.isActive(), DepartmentRef.from(p.getDepartment()), studentCount);
        }
    }

    // ---- Courses ----------------------------------------------------------------------------------

    public record CourseRequest(
            @NotNull Long departmentId,
            @NotBlank @Size(max = 15) @Pattern(regexp = CODE_PATTERN, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 4000) String description,
            @NotNull @Min(1) @Max(10) Integer credits,
            @NotNull @Min(100) @Max(900) Integer level,
            Boolean active) {
    }

    public record PrerequisitesRequest(@NotNull @Size(max = 20) List<@NotNull Long> prerequisiteIds) {
    }

    public record CourseSummary(
            Long id, String code, String title, int credits, int level, boolean active,
            DepartmentRef department, long prerequisiteCount) {

        public static CourseSummary from(Course c, long prerequisiteCount) {
            return new CourseSummary(c.getId(), c.getCode(), c.getTitle(), c.getCredits(), c.getLevel(), c.isActive(),
                    DepartmentRef.from(c.getDepartment()), prerequisiteCount);
        }
    }

    public record CourseDetail(
            Long id, String code, String title, String description, int credits, int level, boolean active,
            DepartmentRef department, List<CourseRef> prerequisites, Instant createdAt, Instant updatedAt) {

        public static CourseDetail from(Course c) {
            return new CourseDetail(c.getId(), c.getCode(), c.getTitle(), c.getDescription(), c.getCredits(),
                    c.getLevel(), c.isActive(), DepartmentRef.from(c.getDepartment()),
                    c.getPrerequisites().stream().sorted(Comparator.comparing(Course::getCode)).map(CourseRef::from).toList(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
