-- V5: grading scale (data, not code) and per-student course results.

CREATE TABLE grading_scales (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_grading_scales_name ON grading_scales (lower(name));
-- Exactly one scale grades new results.
CREATE UNIQUE INDEX ux_grading_scales_single_active ON grading_scales (is_active) WHERE is_active;

-- A band covers marks from min_mark up to (not including) the next band's min_mark, so bands can never
-- overlap or leave gaps; the lowest band must start at 0 (checked by the application).
CREATE TABLE grade_bands (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scale_id      BIGINT        NOT NULL REFERENCES grading_scales (id) ON DELETE CASCADE,
    letter        VARCHAR(3)    NOT NULL,
    min_mark      NUMERIC(5, 2) NOT NULL,
    grade_points  NUMERIC(3, 2) NOT NULL,
    passing       BOOLEAN       NOT NULL,
    CONSTRAINT uq_grade_bands_letter UNIQUE (scale_id, letter),
    CONSTRAINT uq_grade_bands_min_mark UNIQUE (scale_id, min_mark),
    CONSTRAINT ck_grade_bands_min_mark CHECK (min_mark BETWEEN 0 AND 100),
    CONSTRAINT ck_grade_bands_points CHECK (grade_points BETWEEN 0 AND 5)
);

INSERT INTO grading_scales (name, is_active) VALUES ('Standard 4.0 scale', TRUE);

INSERT INTO grade_bands (scale_id, letter, min_mark, grade_points, passing)
SELECT s.id, b.letter, b.min_mark, b.grade_points, b.passing
FROM grading_scales s,
     (VALUES ('A', 70, 4.00, TRUE),
             ('B+', 65, 3.50, TRUE),
             ('B', 60, 3.00, TRUE),
             ('C+', 55, 2.50, TRUE),
             ('C', 50, 2.00, TRUE),
             ('D', 40, 1.00, TRUE),
             ('F', 0, 0.00, FALSE)) AS b (letter, min_mark, grade_points, passing)
WHERE s.is_active;

-- Marks for one registered course. The registration item already identifies student, offering, course and
-- semester, so no foreign keys are duplicated here. grade/grade_points/passed are written once, at
-- finalization, from the scale active at that moment.
CREATE TABLE results (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    registration_item_id  BIGINT        NOT NULL REFERENCES registration_items (id),
    cat_mark              NUMERIC(5, 2),
    exam_mark             NUMERIC(5, 2),
    total_mark            NUMERIC(5, 2),
    grade                 VARCHAR(3),
    grade_points          NUMERIC(3, 2),
    passed                BOOLEAN,
    finalized_at          TIMESTAMPTZ,
    entered_by            BIGINT        REFERENCES users (id),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_results_registration_item UNIQUE (registration_item_id),
    CONSTRAINT ck_results_marks CHECK (cat_mark >= 0 AND exam_mark >= 0 AND total_mark BETWEEN 0 AND 100),
    CONSTRAINT ck_results_total CHECK (
        total_mark IS NULL OR (cat_mark IS NOT NULL AND exam_mark IS NOT NULL AND total_mark = cat_mark + exam_mark)),
    CONSTRAINT ck_results_finalized CHECK (
        finalized_at IS NULL OR (total_mark IS NOT NULL AND grade IS NOT NULL AND grade_points IS NOT NULL AND passed IS NOT NULL))
);

-- Results lifecycle bookkeeping on the offering (marks are submitted and finalized per class).
ALTER TABLE course_offerings
    ADD COLUMN results_submitted_at    TIMESTAMPTZ,
    ADD COLUMN results_finalized_at    TIMESTAMPTZ,
    ADD COLUMN results_finalized_by    BIGINT REFERENCES users (id),
    ADD COLUMN results_return_comment  VARCHAR(500);

CREATE INDEX ix_course_offerings_semester_results ON course_offerings (semester_id, results_status);
