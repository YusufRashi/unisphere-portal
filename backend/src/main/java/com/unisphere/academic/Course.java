package com.unisphere.academic;

import com.unisphere.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A unit of study in the catalogue, independent of when it is taught (see CourseOffering). */
@Getter
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 15)
    private String code;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int credits;

    /** 100 = first-year course, 200 = second-year, ... */
    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "course_prerequisites",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_id"))
    private Set<Course> prerequisites = new HashSet<>();

    public Course(Department department, String code, String title, String description, int credits, int level) {
        update(department, code, title, description, credits, level, true);
    }

    public void update(Department department, String code, String title, String description, int credits, int level,
                       boolean active) {
        this.department = department;
        this.code = Department.normalizeCode(code);
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.credits = credits;
        this.level = level;
        this.active = active;
    }

    public Set<Course> getPrerequisites() {
        return Collections.unmodifiableSet(prerequisites);
    }

    /** Caller must have verified that this does not introduce a cycle (see PrerequisiteGraph). */
    public void replacePrerequisites(Collection<Course> newPrerequisites) {
        prerequisites.clear();
        prerequisites.addAll(newPrerequisites);
    }
}
