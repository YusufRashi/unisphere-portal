-- V7: fee ledger. Money is NUMERIC(12,2), never floating point. Nothing here is ever deleted: mistakes are
-- corrected by voiding a charge or reversing a payment, which keeps the full history. The balance is never
-- stored; it is always Σ active charges − Σ completed payments.

CREATE TABLE fee_accounts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id  BIGINT       NOT NULL REFERENCES students (id),
    currency    CHAR(3)      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_fee_accounts_student UNIQUE (student_id),
    CONSTRAINT ck_fee_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);

-- What a program costs per semester (a flat fee).
CREATE TABLE fee_structures (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    program_id   BIGINT         NOT NULL REFERENCES programs (id),
    semester_id  BIGINT         NOT NULL REFERENCES semesters (id),
    amount       NUMERIC(12, 2) NOT NULL,
    description  VARCHAR(120)   NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_fee_structures_program_semester UNIQUE (program_id, semester_id),
    CONSTRAINT ck_fee_structures_amount CHECK (amount > 0)
);

CREATE TABLE fee_charges (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id        BIGINT         NOT NULL REFERENCES fee_accounts (id),
    semester_id       BIGINT         REFERENCES semesters (id),
    -- Set for charges generated from a fee structure; NULL for manual charges.
    fee_structure_id  BIGINT         REFERENCES fee_structures (id),
    kind              VARCHAR(20)    NOT NULL,
    description       VARCHAR(200)   NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by        BIGINT         REFERENCES users (id),
    created_at        TIMESTAMPTZ    NOT NULL,
    voided_at         TIMESTAMPTZ,
    voided_by         BIGINT         REFERENCES users (id),
    void_reason       VARCHAR(500),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_fee_charges_amount CHECK (amount > 0),
    CONSTRAINT ck_fee_charges_kind CHECK (kind IN ('TUITION', 'MANUAL')),
    CONSTRAINT ck_fee_charges_kind_structure CHECK ((kind = 'TUITION') = (fee_structure_id IS NOT NULL)),
    CONSTRAINT ck_fee_charges_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT ck_fee_charges_void CHECK (status = 'ACTIVE' OR (voided_at IS NOT NULL AND void_reason IS NOT NULL))
);

-- Generating charges twice can never bill a student twice for the same fee structure.
CREATE UNIQUE INDEX ux_fee_charges_structure ON fee_charges (account_id, fee_structure_id) WHERE fee_structure_id IS NOT NULL;
CREATE INDEX ix_fee_charges_account ON fee_charges (account_id);

-- No column exists for card numbers, CVVs or any other payment credential.
CREATE TABLE payments (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id       BIGINT         NOT NULL REFERENCES fee_accounts (id),
    amount           NUMERIC(12, 2) NOT NULL,
    method           VARCHAR(20)    NOT NULL,
    reference        VARCHAR(60)    NOT NULL,
    channel          VARCHAR(20)    NOT NULL,
    provider         VARCHAR(30),
    provider_txn_id  VARCHAR(80),
    -- Client-supplied key that makes a retried online payment a no-op.
    idempotency_key  VARCHAR(64),
    status           VARCHAR(20)    NOT NULL,
    failure_reason   VARCHAR(200),
    paid_at          TIMESTAMPTZ    NOT NULL,
    recorded_by      BIGINT         REFERENCES users (id),
    note             VARCHAR(200),
    reversed_at      TIMESTAMPTZ,
    reversed_by      BIGINT         REFERENCES users (id),
    reversal_reason  VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'MOBILE_MONEY', 'CARD')),
    CONSTRAINT ck_payments_channel CHECK (channel IN ('OFFICE', 'ONLINE')),
    CONSTRAINT ck_payments_status CHECK (status IN ('COMPLETED', 'FAILED', 'REVERSED')),
    CONSTRAINT ck_payments_failed CHECK (status <> 'FAILED' OR failure_reason IS NOT NULL),
    CONSTRAINT ck_payments_reversed CHECK (status <> 'REVERSED' OR (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL))
);

-- A receipt or transaction reference can be recorded only once (case-insensitive).
CREATE UNIQUE INDEX ux_payments_reference ON payments (upper(reference));
CREATE UNIQUE INDEX ux_payments_idempotency ON payments (account_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_payments_account_paid ON payments (account_id, paid_at DESC);
CREATE INDEX ix_payments_paid_at ON payments (paid_at);
