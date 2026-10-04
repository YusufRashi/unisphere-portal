-- V4: course registration. One registration per student per semester; its items are the chosen offerings.

CREATE TABLE registrations (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id        BIGINT       NOT NULL REFERENCES students (id),
    semester_id       BIGINT       NOT NULL REFERENCES semesters (id),
    status            VARCHAR(20)  NOT NULL,
    submitted_at      TIMESTAMPTZ,
    reviewed_by       BIGINT       REFERENCES users (id),
    reviewed_at       TIMESTAMPTZ,
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_registrations_student_semester UNIQUE (student_id, semester_id),
    CONSTRAINT ck_registrations_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_registrations_rejection CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

CREATE INDEX ix_registrations_semester_status ON registrations (semester_id, status);

CREATE TABLE registration_items (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    registration_id  BIGINT       NOT NULL REFERENCES registrations (id) ON DELETE CASCADE,
    offering_id      BIGINT       NOT NULL REFERENCES course_offerings (id),
    status           VARCHAR(20)  NOT NULL,
    -- True while this item holds one of the offering's seats (counted in course_offerings.enrolled_count).
    seat_reserved    BOOLEAN      NOT NULL DEFAULT FALSE,
    added_at         TIMESTAMPTZ  NOT NULL,
    dropped_at       TIMESTAMPTZ,
    CONSTRAINT ck_registration_items_status CHECK (status IN ('ACTIVE', 'DROPPED')),
    CONSTRAINT ck_registration_items_dropped CHECK (status = 'ACTIVE' OR (dropped_at IS NOT NULL AND NOT seat_reserved))
);

-- An offering can appear only once among a registration's active items (a dropped one may be re-added).
CREATE UNIQUE INDEX ux_registration_items_active ON registration_items (registration_id, offering_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_registration_items_offering_active ON registration_items (offering_id) WHERE status = 'ACTIVE';
