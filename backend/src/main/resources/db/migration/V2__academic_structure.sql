-- V2: academic structure (departments, programs, courses, prerequisites) and people profiles.

CREATE TABLE departments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(10)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    faculty     VARCHAR(150),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_departments_code ON departments (upper(code));
CREATE UNIQUE INDEX ux_departments_name ON departments (lower(name));

CREATE TABLE programs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    department_id   BIGINT       NOT NULL REFERENCES departments (id),
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    award_level     VARCHAR(20)  NOT NULL,
    duration_years  INT          NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_programs_award_level CHECK (award_level IN ('CERTIFICATE', 'DIPLOMA', 'BACHELOR', 'MASTER', 'DOCTORATE')),
    CONSTRAINT ck_programs_duration CHECK (duration_years BETWEEN 1 AND 8)
);

CREATE UNIQUE INDEX ux_programs_code ON programs (upper(code));
CREATE INDEX ix_programs_department ON programs (department_id);

CREATE TABLE courses (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    department_id  BIGINT       NOT NULL REFERENCES departments (id),
    code           VARCHAR(15)  NOT NULL,
    title          VARCHAR(150) NOT NULL,
    description    TEXT,
    credits        INT          NOT NULL,
    level          INT          NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_courses_credits CHECK (credits BETWEEN 1 AND 10),
    CONSTRAINT ck_courses_level CHECK (level BETWEEN 100 AND 900 AND level % 100 = 0)
);

CREATE UNIQUE INDEX ux_courses_code ON courses (upper(code));
CREATE INDEX ix_courses_department_level ON courses (department_id, level);

-- Self-referencing many-to-many. Longer cycles (A→B→A) are rejected by the service.
CREATE TABLE course_prerequisites (
    course_id        BIGINT NOT NULL REFERENCES courses (id),
    prerequisite_id  BIGINT NOT NULL REFERENCES courses (id),
    PRIMARY KEY (course_id, prerequisite_id),
    CONSTRAINT ck_course_prerequisites_not_self CHECK (course_id <> prerequisite_id)
);

CREATE INDEX ix_course_prerequisites_prerequisite ON course_prerequisites (prerequisite_id);

-- Human-readable identifiers are generated from sequences so they never collide under concurrency.
CREATE SEQUENCE student_number_seq START 1;
CREATE SEQUENCE staff_number_seq START 1;

CREATE TABLE students (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE REFERENCES users (id),
    student_number  VARCHAR(20)  NOT NULL UNIQUE,
    program_id      BIGINT       NOT NULL REFERENCES programs (id),
    year_of_study   INT          NOT NULL,
    admission_date  DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_students_year CHECK (year_of_study BETWEEN 1 AND 8),
    CONSTRAINT ck_students_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'GRADUATED', 'WITHDRAWN'))
);

CREATE INDEX ix_students_program ON students (program_id);
CREATE INDEX ix_students_status ON students (status);

CREATE TABLE lecturers (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL UNIQUE REFERENCES users (id),
    staff_number   VARCHAR(20)  NOT NULL UNIQUE,
    department_id  BIGINT       NOT NULL REFERENCES departments (id),
    title          VARCHAR(30),
    office         VARCHAR(60),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_lecturers_department ON lecturers (department_id);
