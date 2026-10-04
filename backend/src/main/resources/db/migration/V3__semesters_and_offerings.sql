-- V3: academic calendar and course offerings (a course taught in a semester).

CREATE TABLE semesters (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                    VARCHAR(20)  NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    academic_year           VARCHAR(9)   NOT NULL,
    term                    INT          NOT NULL,
    start_date              DATE         NOT NULL,
    end_date                DATE         NOT NULL,
    registration_opens_at   TIMESTAMPTZ  NOT NULL,
    registration_closes_at  TIMESTAMPTZ  NOT NULL,
    add_drop_deadline       TIMESTAMPTZ  NOT NULL,
    registration_open       BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_approval       BOOLEAN      NOT NULL DEFAULT FALSE,
    min_credits             INT          NOT NULL DEFAULT 12,
    max_credits             INT          NOT NULL DEFAULT 24,
    is_current              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_semesters_term CHECK (term BETWEEN 1 AND 3),
    CONSTRAINT ck_semesters_academic_year CHECK (academic_year ~ '^[0-9]{4}/[0-9]{4}$'),
    CONSTRAINT ck_semesters_dates CHECK (end_date > start_date),
    CONSTRAINT ck_semesters_registration_window CHECK (registration_closes_at > registration_opens_at),
    CONSTRAINT ck_semesters_add_drop CHECK (add_drop_deadline >= registration_opens_at),
    CONSTRAINT ck_semesters_credits CHECK (min_credits >= 0 AND max_credits >= min_credits AND max_credits <= 60)
);

CREATE UNIQUE INDEX ux_semesters_code ON semesters (upper(code));
-- At most one semester can be the current one.
CREATE UNIQUE INDEX ux_semesters_single_current ON semesters (is_current) WHERE is_current;

CREATE TABLE course_offerings (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id        BIGINT        NOT NULL REFERENCES courses (id),
    semester_id      BIGINT        NOT NULL REFERENCES semesters (id),
    section          VARCHAR(5)    NOT NULL DEFAULT 'A',
    lecturer_id      BIGINT        REFERENCES lecturers (id),
    capacity         INT           NOT NULL,
    -- Maintained with guarded atomic updates in Phase 3; the CHECK is the last line of defence.
    enrolled_count   INT           NOT NULL DEFAULT 0,
    cat_weight       NUMERIC(5, 2) NOT NULL DEFAULT 30,
    exam_weight      NUMERIC(5, 2) NOT NULL DEFAULT 70,
    exam_starts_at   TIMESTAMPTZ,
    exam_venue       VARCHAR(80),
    results_status   VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_course_offerings_section UNIQUE (course_id, semester_id, section),
    CONSTRAINT ck_course_offerings_capacity CHECK (capacity BETWEEN 1 AND 1000),
    CONSTRAINT ck_course_offerings_enrolled CHECK (enrolled_count BETWEEN 0 AND capacity),
    CONSTRAINT ck_course_offerings_weights CHECK (
        cat_weight >= 0 AND exam_weight >= 0 AND cat_weight + exam_weight = 100),
    CONSTRAINT ck_course_offerings_results_status CHECK (results_status IN ('DRAFT', 'SUBMITTED', 'FINALIZED'))
);

CREATE INDEX ix_course_offerings_semester ON course_offerings (semester_id);
CREATE INDEX ix_course_offerings_lecturer ON course_offerings (lecturer_id);

CREATE TABLE offering_sessions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id  BIGINT      NOT NULL REFERENCES course_offerings (id) ON DELETE CASCADE,
    day_of_week  INT         NOT NULL,
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    venue        VARCHAR(80) NOT NULL,
    CONSTRAINT ck_offering_sessions_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_offering_sessions_time CHECK (end_time > start_time)
);

CREATE INDEX ix_offering_sessions_offering ON offering_sessions (offering_id);
