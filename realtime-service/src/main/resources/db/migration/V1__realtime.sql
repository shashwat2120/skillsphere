-- =============================================================================
-- V1 — Real-time / live arena, this service's own database
--
-- Same approach as every other service's own V1: the final shape these 5
-- tables had already reached (monolith V8 + V10's version/created_at/
-- updated_at additions on arena_participants + V16's uppercase literals)
-- collapsed into one clean "service birth" migration.
--
-- Foreign keys NOT reproduced — same reasoning as assessment-service's own
-- V1 header comment:
--   - instructor_id / user_id / acknowledged_by -> users(id): identity-
--     service's database now.
--   - course_id -> courses(id): content-service's database now. Not
--     mirrored either — nothing in this service ever turns a course_id
--     into a display name, same reasoning as analytics-service's own V1.
--   - skill_id -> skills(id), item_id -> items(id), selected_option_id ->
--     item_options(id): assessment-service's database now. skills and
--     items ARE mirrored (see V2, V3) since ArenaService and
--     ConfusionDetectionService genuinely need names and full question
--     content, not just opaque ids.
--   - misconception_id -> misconceptions(id): assessment-service's
--     database. Not mirrored — confusion_signals.misconception_id is a
--     "which misconception dominated" annotation nothing here currently
--     reads back out, the same low-stakes soft-reference treatment
--     misconceptions.remediation_lesson_id gets in assessment-service's
--     own V1.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- arenas
-- -----------------------------------------------------------------------------
CREATE TABLE arenas (
    id              BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    instructor_id   BIGINT       NOT NULL,
    -- No foreign key — see this file's header comment.
    course_id       BIGINT,
    -- No foreign key to assessment-service's skills; see skills_mirror (V2).
    skill_id        BIGINT,
    title           VARCHAR(200) NOT NULL,
    join_code       VARCHAR(10)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'LOBBY',
    allow_guests    BOOLEAN      NOT NULL DEFAULT TRUE,
    question_count  INT          NOT NULL DEFAULT 10,
    seconds_per_q   INT          NOT NULL DEFAULT 20,
    participant_count INT        NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_arena_join_code UNIQUE (join_code),
    CONSTRAINT ck_arena_status     CHECK (status IN ('LOBBY', 'RUNNING', 'PAUSED', 'ENDED')),
    CONSTRAINT ck_arena_timing     CHECK (seconds_per_q BETWEEN 5 AND 300)
);
COMMENT ON COLUMN arenas.join_code IS 'Short human-typable code backing the QR join link, so participants never need an account or a password to take part.';

CREATE INDEX idx_arena_instructor ON arenas (instructor_id, created_at DESC);
CREATE INDEX idx_arena_live       ON arenas (status) WHERE status IN ('LOBBY', 'RUNNING');

-- -----------------------------------------------------------------------------
-- arena_participants
-- -----------------------------------------------------------------------------
CREATE TABLE arena_participants (
    id            BIGSERIAL PRIMARY KEY,
    arena_id      BIGINT       NOT NULL,
    -- No foreign key — see this file's header comment.
    user_id       BIGINT,
    display_name  VARCHAR(60)  NOT NULL,
    guest_token   VARCHAR(64),
    score         INT          NOT NULL DEFAULT 0,
    correct_count INT          NOT NULL DEFAULT 0,
    answer_count  INT          NOT NULL DEFAULT 0,
    final_rank    INT,
    joined_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    left_at       TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT fk_ap_arena FOREIGN KEY (arena_id) REFERENCES arenas (id) ON DELETE CASCADE,
    CONSTRAINT ck_ap_identity CHECK (user_id IS NOT NULL OR guest_token IS NOT NULL)
);
COMMENT ON COLUMN arena_participants.version IS 'Optimistic lock. Scores update on every answer during a live session — a conflict means a concurrent write was prevented, not that something failed.';

CREATE UNIQUE INDEX uq_ap_arena_user  ON arena_participants (arena_id, user_id)     WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ap_arena_guest ON arena_participants (arena_id, guest_token) WHERE guest_token IS NOT NULL;
CREATE INDEX idx_ap_arena_score       ON arena_participants (arena_id, score DESC);

-- -----------------------------------------------------------------------------
-- arena_questions
-- -----------------------------------------------------------------------------
CREATE TABLE arena_questions (
    id           BIGSERIAL PRIMARY KEY,
    arena_id     BIGINT      NOT NULL,
    -- No foreign key to assessment-service's items; see items_mirror (V3).
    item_id      BIGINT      NOT NULL,
    position     INT         NOT NULL,
    time_limit_sec INT       NOT NULL DEFAULT 20,
    published_at TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,

    CONSTRAINT uq_arena_question_pos UNIQUE (arena_id, position),
    CONSTRAINT fk_aq_arena FOREIGN KEY (arena_id) REFERENCES arenas (id) ON DELETE CASCADE
);
CREATE INDEX idx_aq_arena ON arena_questions (arena_id, position);

-- -----------------------------------------------------------------------------
-- arena_answers
-- -----------------------------------------------------------------------------
CREATE TABLE arena_answers (
    id                 BIGSERIAL PRIMARY KEY,
    arena_question_id  BIGINT      NOT NULL,
    participant_id     BIGINT      NOT NULL,
    -- No foreign key to assessment-service's item_options; the id is still
    -- meaningful (it identifies one of items_mirror's option rows), just
    -- not database-enforced across services.
    selected_option_id BIGINT,
    is_correct         BOOLEAN     NOT NULL DEFAULT FALSE,
    response_time_ms   INT,
    points_awarded     INT         NOT NULL DEFAULT 0,
    answered_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_arena_answer  UNIQUE (arena_question_id, participant_id),
    CONSTRAINT fk_aa_question   FOREIGN KEY (arena_question_id)  REFERENCES arena_questions (id)   ON DELETE CASCADE,
    CONSTRAINT fk_aa_participant FOREIGN KEY (participant_id)    REFERENCES arena_participants (id) ON DELETE CASCADE
);
COMMENT ON TABLE arena_answers IS 'The UNIQUE constraint enforces one answer per participant per question — late or duplicate submissions from a flaky mobile connection cannot double-score.';

CREATE INDEX idx_aa_question ON arena_answers (arena_question_id);

-- -----------------------------------------------------------------------------
-- confusion_signals — fires the live instructor alert
-- -----------------------------------------------------------------------------
CREATE TABLE confusion_signals (
    id                    BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id               BIGINT      NOT NULL,
    -- No foreign key to assessment-service's skills; see skills_mirror (V2).
    skill_id              BIGINT      NOT NULL,
    -- No foreign key — see this file's header comment.
    course_id             BIGINT,
    failure_count         INT         NOT NULL,
    -- No foreign key — see this file's header comment.
    misconception_id      BIGINT,
    severity              VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    window_start          TIMESTAMPTZ NOT NULL,
    detected_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    instructor_notified_at TIMESTAMPTZ,
    -- No foreign key — see this file's header comment.
    acknowledged_by       BIGINT,
    resolved_at           TIMESTAMPTZ,

    CONSTRAINT ck_cs_severity      CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH'))
);

COMMENT ON TABLE confusion_signals IS
'Raised when a learner fails N related items inside a time window. This is the AI-plus-human loop: the system does not replace the instructor, it tells the instructor exactly where to look, while the learner is still stuck.';

CREATE INDEX idx_cs_open        ON confusion_signals (course_id, detected_at DESC) WHERE resolved_at IS NULL;
CREATE INDEX idx_cs_user_skill  ON confusion_signals (user_id, skill_id, detected_at DESC);
