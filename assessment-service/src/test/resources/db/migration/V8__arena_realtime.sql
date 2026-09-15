-- =============================================================================
-- V8 — Live Arena & Real-time
-- The live classroom layer: QR-joined competitive sessions, and the confusion
-- signals that alert an instructor while a learner is still struggling.
--
-- Live ranking is served from Redis sorted sets. These tables are the durable
-- record — Postgres would be the bottleneck under per-answer live load.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- arenas
-- -----------------------------------------------------------------------------
CREATE TABLE arenas (
    id              BIGSERIAL PRIMARY KEY,
    instructor_id   BIGINT       NOT NULL,
    course_id       BIGINT,
    skill_id        BIGINT,
    title           VARCHAR(200) NOT NULL,
    join_code       VARCHAR(10)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'lobby',
    -- Allow unauthenticated guests to join by QR (used in demos and lectures)
    allow_guests    BOOLEAN      NOT NULL DEFAULT TRUE,
    question_count  INT          NOT NULL DEFAULT 10,
    seconds_per_q   INT          NOT NULL DEFAULT 20,
    participant_count INT        NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_arena_join_code UNIQUE (join_code),
    CONSTRAINT fk_arena_instructor FOREIGN KEY (instructor_id) REFERENCES users (id)   ON DELETE CASCADE,
    CONSTRAINT fk_arena_course     FOREIGN KEY (course_id)     REFERENCES courses (id) ON DELETE SET NULL,
    CONSTRAINT fk_arena_skill      FOREIGN KEY (skill_id)      REFERENCES skills (id)  ON DELETE SET NULL,
    CONSTRAINT ck_arena_status     CHECK (status IN ('lobby', 'running', 'paused', 'ended')),
    CONSTRAINT ck_arena_timing     CHECK (seconds_per_q BETWEEN 5 AND 300)
);
COMMENT ON COLUMN arenas.join_code IS 'Short human-typable code backing the QR join link, so participants never need an account or a password to take part.';

CREATE INDEX idx_arena_instructor ON arenas (instructor_id, created_at DESC);
CREATE INDEX idx_arena_live       ON arenas (status) WHERE status IN ('lobby', 'running');

-- -----------------------------------------------------------------------------
-- arena_participants
-- -----------------------------------------------------------------------------
CREATE TABLE arena_participants (
    id            BIGSERIAL PRIMARY KEY,
    arena_id      BIGINT       NOT NULL,
    user_id       BIGINT,
    display_name  VARCHAR(60)  NOT NULL,
    -- Stable id for guests, who have no user_id
    guest_token   VARCHAR(64),
    score         INT          NOT NULL DEFAULT 0,
    correct_count INT          NOT NULL DEFAULT 0,
    answer_count  INT          NOT NULL DEFAULT 0,
    final_rank    INT,
    joined_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    left_at       TIMESTAMPTZ,

    CONSTRAINT fk_ap_arena FOREIGN KEY (arena_id) REFERENCES arenas (id) ON DELETE CASCADE,
    CONSTRAINT fk_ap_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE SET NULL,
    -- Either a signed-in user or a guest token, never neither
    CONSTRAINT ck_ap_identity CHECK (user_id IS NOT NULL OR guest_token IS NOT NULL)
);
CREATE UNIQUE INDEX uq_ap_arena_user  ON arena_participants (arena_id, user_id)     WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ap_arena_guest ON arena_participants (arena_id, guest_token) WHERE guest_token IS NOT NULL;
CREATE INDEX idx_ap_arena_score       ON arena_participants (arena_id, score DESC);

-- -----------------------------------------------------------------------------
-- arena_questions
-- -----------------------------------------------------------------------------
CREATE TABLE arena_questions (
    id           BIGSERIAL PRIMARY KEY,
    arena_id     BIGINT      NOT NULL,
    item_id      BIGINT      NOT NULL,
    position     INT         NOT NULL,
    time_limit_sec INT       NOT NULL DEFAULT 20,
    published_at TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,

    CONSTRAINT uq_arena_question_pos UNIQUE (arena_id, position),
    CONSTRAINT fk_aq_arena FOREIGN KEY (arena_id) REFERENCES arenas (id) ON DELETE CASCADE,
    CONSTRAINT fk_aq_item  FOREIGN KEY (item_id)  REFERENCES items (id)  ON DELETE CASCADE
);
CREATE INDEX idx_aq_arena ON arena_questions (arena_id, position);

-- -----------------------------------------------------------------------------
-- arena_answers
-- -----------------------------------------------------------------------------
CREATE TABLE arena_answers (
    id                 BIGSERIAL PRIMARY KEY,
    arena_question_id  BIGINT      NOT NULL,
    participant_id     BIGINT      NOT NULL,
    selected_option_id BIGINT,
    is_correct         BOOLEAN     NOT NULL DEFAULT FALSE,
    response_time_ms   INT,
    -- Base points plus a speed bonus, so quick correct answers rank higher
    points_awarded     INT         NOT NULL DEFAULT 0,
    answered_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_arena_answer  UNIQUE (arena_question_id, participant_id),
    CONSTRAINT fk_aa_question   FOREIGN KEY (arena_question_id)  REFERENCES arena_questions (id)   ON DELETE CASCADE,
    CONSTRAINT fk_aa_participant FOREIGN KEY (participant_id)    REFERENCES arena_participants (id) ON DELETE CASCADE,
    CONSTRAINT fk_aa_option     FOREIGN KEY (selected_option_id) REFERENCES item_options (id)      ON DELETE SET NULL
);
COMMENT ON TABLE arena_answers IS 'The UNIQUE constraint enforces one answer per participant per question — late or duplicate submissions from a flaky mobile connection cannot double-score.';

CREATE INDEX idx_aa_question ON arena_answers (arena_question_id);

-- -----------------------------------------------------------------------------
-- confusion_signals — fires the live instructor alert
-- -----------------------------------------------------------------------------
CREATE TABLE confusion_signals (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL,
    skill_id              BIGINT      NOT NULL,
    course_id             BIGINT,
    failure_count         INT         NOT NULL,
    -- Dominant misconception across the failures, when one stands out
    misconception_id      BIGINT,
    severity              VARCHAR(20) NOT NULL DEFAULT 'medium',
    window_start          TIMESTAMPTZ NOT NULL,
    detected_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    instructor_notified_at TIMESTAMPTZ,
    acknowledged_by       BIGINT,
    resolved_at           TIMESTAMPTZ,

    CONSTRAINT fk_cs_user          FOREIGN KEY (user_id)          REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_cs_skill         FOREIGN KEY (skill_id)         REFERENCES skills (id)         ON DELETE CASCADE,
    CONSTRAINT fk_cs_course        FOREIGN KEY (course_id)        REFERENCES courses (id)        ON DELETE SET NULL,
    CONSTRAINT fk_cs_misconception FOREIGN KEY (misconception_id) REFERENCES misconceptions (id) ON DELETE SET NULL,
    CONSTRAINT fk_cs_ack           FOREIGN KEY (acknowledged_by)  REFERENCES users (id)          ON DELETE SET NULL,
    CONSTRAINT ck_cs_severity      CHECK (severity IN ('low', 'medium', 'high'))
);

COMMENT ON TABLE confusion_signals IS
'Raised when a learner fails N related items inside a time window. This is the AI-plus-human loop: the system does not replace the instructor, it tells the instructor exactly where to look, while the learner is still stuck.';

CREATE INDEX idx_cs_open        ON confusion_signals (course_id, detected_at DESC) WHERE resolved_at IS NULL;
CREATE INDEX idx_cs_user_skill  ON confusion_signals (user_id, skill_id, detected_at DESC);
