-- V8: in-app notifications and course announcements.

CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id),
    -- Stored as text (like audit actions) so a new kind of notification only needs a new enum constant.
    type        VARCHAR(40)   NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    message     TEXT          NOT NULL,
    -- A path inside the web app, never an external URL.
    link        VARCHAR(300),
    -- Set when one event must reach a user at most once (e.g. one reminder per deadline), so a job that runs
    -- twice, or on two instances at once, cannot send duplicates.
    dedupe_key  VARCHAR(100),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_notifications_link CHECK (link IS NULL OR (link LIKE '/%' AND link NOT LIKE '//%'))
);

CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
CREATE UNIQUE INDEX ux_notifications_dedupe ON notifications (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE announcements (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offering_id  BIGINT        NOT NULL REFERENCES course_offerings (id),
    author_id    BIGINT        NOT NULL REFERENCES users (id),
    title        VARCHAR(200)  NOT NULL,
    body         TEXT          NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_announcements_body CHECK (length(body) BETWEEN 1 AND 5000)
);

CREATE INDEX ix_announcements_offering ON announcements (offering_id, created_at DESC);
