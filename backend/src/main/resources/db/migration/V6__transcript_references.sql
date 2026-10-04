-- V6: verification references for issued transcripts.
--
-- A transcript's reference is derived from its content (student and every finalized grade), so the same
-- record always gets the same reference and any later change to the record produces a new one. Storing
-- each reference lets the registrar check a printed transcript: an unknown reference, a different student
-- or a different CGPA means the document was not issued by the system as presented.

CREATE TABLE transcript_references (
    reference        VARCHAR(19)   PRIMARY KEY,
    student_id       BIGINT        NOT NULL REFERENCES students (id),
    content_sha256   CHAR(64)      NOT NULL,
    cgpa             NUMERIC(3, 2),
    credits_earned   INT           NOT NULL,
    results_count    INT           NOT NULL,
    first_issued_at  TIMESTAMPTZ   NOT NULL,
    last_issued_at   TIMESTAMPTZ   NOT NULL,
    issue_count      INT           NOT NULL DEFAULT 1,
    CONSTRAINT ck_transcript_references_format CHECK (reference ~ '^[0-9A-F]{4}(-[0-9A-F]{4}){3}$'),
    CONSTRAINT ck_transcript_references_counts CHECK (credits_earned >= 0 AND results_count >= 0 AND issue_count >= 1)
);

CREATE INDEX ix_transcript_references_student ON transcript_references (student_id);
