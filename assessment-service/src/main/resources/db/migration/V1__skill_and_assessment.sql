-- =============================================================================
-- V1 — Skill graph + assessment, this service's own database
--
-- Same approach as identity-service's own V1: the FINAL shape these 11
-- tables had already reached in the shared database — uppercase enum
-- literals (monolith V12), the one-in-progress-assessment-per-user partial
-- index (monolith V13) — collapsed into one clean "service birth" migration
-- rather than replayed as a multi-file history that never actually applied
-- to this database.
--
-- Two kinds of foreign key from the monolith's originals are deliberately
-- NOT reproduced here, for the same reason identity-service's V18 (in the
-- monolith) explains at length: a plain, unenforced column replaces each.
--
--   - user_id / author_id -> users(id): identity owns that table now, in a
--     different physical database. Every writer is either a self-referencing
--     write from an already-JWT-authenticated caller, or role-gated.
--   - misconceptions.remediation_lesson_id -> lessons(id): content-service
--     owns that table. This was always a soft, optional pointer ("a lesson
--     that directly addresses this misunderstanding, if one exists") rather
--     than a relationship anything's correctness depends on, so it gets no
--     replacement — not even a read-model mirror, the treatment skills and
--     items get elsewhere in this split (see the read-model migrations in
--     content-service, career-service, verification-service, realtime-
--     service and analytics-service) — because nothing reads it back
--     programmatically today.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- skill_categories
-- -----------------------------------------------------------------------------
CREATE TABLE skill_categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    colour      VARCHAR(7),
    position    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_skill_category_slug UNIQUE (slug)
);

-- -----------------------------------------------------------------------------
-- skills — graph nodes
-- -----------------------------------------------------------------------------
CREATE TABLE skills (
    id            BIGSERIAL PRIMARY KEY,
    slug          VARCHAR(120) NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    category_id   BIGINT,
    level_band    VARCHAR(20)  NOT NULL DEFAULT 'INTERMEDIATE',
    est_minutes   INT          NOT NULL DEFAULT 60,
    decay_rate    NUMERIC(6,5) NOT NULL DEFAULT 0.00200,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT uq_skills_slug     UNIQUE (slug),
    CONSTRAINT fk_skills_category FOREIGN KEY (category_id) REFERENCES skill_categories (id) ON DELETE SET NULL,
    CONSTRAINT ck_skills_band     CHECK (level_band IN ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    CONSTRAINT ck_skills_decay    CHECK (decay_rate >= 0 AND decay_rate <= 1)
);

COMMENT ON TABLE skills IS 'Nodes of the skill DAG. A skill is the first-class entity of the platform — courses and items exist to serve skills, not the other way round.';

CREATE INDEX idx_skills_category ON skills (category_id) WHERE is_active;
CREATE INDEX idx_skills_band     ON skills (level_band);

-- -----------------------------------------------------------------------------
-- skill_prerequisites — graph edges
-- -----------------------------------------------------------------------------
CREATE TABLE skill_prerequisites (
    id                     BIGSERIAL PRIMARY KEY,
    skill_id               BIGINT       NOT NULL,
    prerequisite_skill_id  BIGINT       NOT NULL,
    strength               NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_skill_prereq        UNIQUE (skill_id, prerequisite_skill_id),
    CONSTRAINT fk_prereq_skill        FOREIGN KEY (skill_id)              REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT fk_prereq_prerequisite FOREIGN KEY (prerequisite_skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT ck_prereq_not_self     CHECK (skill_id <> prerequisite_skill_id),
    CONSTRAINT ck_prereq_strength     CHECK (strength > 0 AND strength <= 1)
);

COMMENT ON TABLE skill_prerequisites IS
'Directed edges of the DAG. Self-loops are blocked by a CHECK, but longer cycles cannot be expressed as a constraint in PostgreSQL — the application runs a reachability test before every insert and rejects any edge that would close a cycle.';

CREATE INDEX idx_prereq_skill  ON skill_prerequisites (skill_id);
CREATE INDEX idx_prereq_prereq ON skill_prerequisites (prerequisite_skill_id);

-- -----------------------------------------------------------------------------
-- learner_skill_state — the live model of a person
--
-- user_id has no foreign key — see this file's header comment. This table
-- is the one place in the whole split where that trade is felt most: it is,
-- in the monolith's own words, "the single most written-to and read-from
-- table in the system." The trade is still correct — every writer here is
-- either DiagnosticService acting on the currently-authenticated user, or a
-- scheduled decay job acting on ids already validated when the row was
-- first created — but it is worth naming plainly rather than only in the
-- generic header comment.
-- -----------------------------------------------------------------------------
CREATE TABLE learner_skill_state (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT        NOT NULL,
    skill_id             BIGINT        NOT NULL,

    ability_theta        NUMERIC(6,4)  NOT NULL DEFAULT 0.0000,
    ability_se           NUMERIC(6,4)  NOT NULL DEFAULT 1.0000,
    mastery_probability  NUMERIC(5,4)  NOT NULL DEFAULT 0.0500,
    elo_rating           INT           NOT NULL DEFAULT 1200,

    attempts_count       INT           NOT NULL DEFAULT 0,
    correct_count        INT           NOT NULL DEFAULT 0,
    peak_mastery         NUMERIC(5,4)  NOT NULL DEFAULT 0.0000,

    first_seen_at        TIMESTAMPTZ,
    last_practiced_at    TIMESTAMPTZ,
    mastered_at          TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_learner_skill      UNIQUE (user_id, skill_id),
    CONSTRAINT fk_lss_skill          FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT ck_lss_mastery        CHECK (mastery_probability >= 0 AND mastery_probability <= 1),
    CONSTRAINT ck_lss_counts         CHECK (correct_count >= 0 AND correct_count <= attempts_count)
);

COMMENT ON TABLE  learner_skill_state IS 'One row per learner per touched skill. The single most written-to and read-from table in the system — every answered item updates it, and every path decision reads it.';
COMMENT ON COLUMN learner_skill_state.ability_se IS 'Standard error of theta. Falls as evidence accumulates; the diagnostic stops when it drops below the confidence threshold.';
COMMENT ON COLUMN learner_skill_state.version IS 'Optimistic lock. A conflict means a concurrent write was prevented, not that something failed — callers retry.';

CREATE INDEX idx_lss_user            ON learner_skill_state (user_id);
CREATE INDEX idx_lss_skill           ON learner_skill_state (skill_id);
CREATE INDEX idx_lss_user_mastery    ON learner_skill_state (user_id, mastery_probability DESC);
CREATE INDEX idx_lss_decay_candidates ON learner_skill_state (last_practiced_at)
    WHERE mastery_probability > 0.5;

-- -----------------------------------------------------------------------------
-- skill_mastery_history — append-only, drives charts and decay
-- -----------------------------------------------------------------------------
CREATE TABLE skill_mastery_history (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL,
    skill_id             BIGINT       NOT NULL,
    mastery_probability  NUMERIC(5,4) NOT NULL,
    ability_theta        NUMERIC(6,4) NOT NULL,
    trigger_type         VARCHAR(30)  NOT NULL,
    source_id            BIGINT,
    recorded_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_smh_skill   FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT ck_smh_trigger CHECK (trigger_type IN
        ('RESPONSE', 'ASSESSMENT', 'PROJECT', 'VIVA', 'INSTRUCTOR', 'DECAY', 'INITIAL'))
);

COMMENT ON TABLE skill_mastery_history IS 'Append-only ledger of every mastery change. Never updated or deleted — it is the evidence behind the progress charts and the decay curve.';

CREATE INDEX idx_smh_user_skill_time ON skill_mastery_history (user_id, skill_id, recorded_at DESC);

-- -----------------------------------------------------------------------------
-- misconceptions — what a wrong answer actually reveals
-- -----------------------------------------------------------------------------
CREATE TABLE misconceptions (
    id                BIGSERIAL PRIMARY KEY,
    skill_id          BIGINT       NOT NULL,
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    remediation_hint  TEXT         NOT NULL,
    -- No foreign key — see this file's header comment. content-service owns
    -- lessons(id) now.
    remediation_lesson_id BIGINT,
    times_observed    INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,

    CONSTRAINT fk_misconception_skill  FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
);

COMMENT ON TABLE misconceptions IS
'A named, specific false belief — e.g. "believes HashMap preserves insertion order". This is what turns "you got it wrong" into "here is what you misunderstand", and it is what the targeted remediation acts on.';

CREATE INDEX idx_misconception_skill ON misconceptions (skill_id);

-- -----------------------------------------------------------------------------
-- items — the self-calibrating question bank
-- -----------------------------------------------------------------------------
CREATE TABLE items (
    id                  BIGSERIAL PRIMARY KEY,
    skill_id            BIGINT       NOT NULL,
    stem                TEXT         NOT NULL,
    type                VARCHAR(20)  NOT NULL DEFAULT 'MCQ',
    explanation         TEXT,

    declared_difficulty NUMERIC(4,2) NOT NULL DEFAULT 0.00,
    difficulty_b        NUMERIC(6,4) NOT NULL DEFAULT 0.0000,
    discrimination_a    NUMERIC(6,4) NOT NULL DEFAULT 1.0000,
    elo_rating          INT          NOT NULL DEFAULT 1200,
    is_calibrated       BOOLEAN      NOT NULL DEFAULT FALSE,

    times_seen          INT          NOT NULL DEFAULT 0,
    times_correct       INT          NOT NULL DEFAULT 0,
    avg_response_ms     INT,

    -- No foreign key — see this file's header comment. identity-service owns
    -- users(id) now.
    author_id           BIGINT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT fk_items_skill   FOREIGN KEY (skill_id)  REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT ck_items_type    CHECK (type IN ('MCQ', 'MULTI_SELECT', 'NUMERIC', 'SHORT_TEXT', 'CODE')),
    CONSTRAINT ck_items_status  CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_items_counts  CHECK (times_correct >= 0 AND times_correct <= times_seen),
    CONSTRAINT ck_items_discrim CHECK (discrimination_a > 0)
);

COMMENT ON COLUMN items.declared_difficulty IS 'The cold-start answer: the author states a difficulty, it seeds the prior, and difficulty_b/elo_rating then correct it from real response data. Kept separately so the prior and the learned value are both visible.';
COMMENT ON COLUMN items.discrimination_a IS 'IRT 2PL discrimination — how sharply this item separates learners near its difficulty. Higher means more informative.';

CREATE INDEX idx_items_skill_active ON items (skill_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_items_difficulty   ON items (skill_id, difficulty_b) WHERE status = 'ACTIVE';
CREATE INDEX idx_items_author       ON items (author_id);

-- -----------------------------------------------------------------------------
-- item_options — every distractor carries a misconception
-- -----------------------------------------------------------------------------
CREATE TABLE item_options (
    id               BIGSERIAL PRIMARY KEY,
    item_id          BIGINT       NOT NULL,
    text             TEXT         NOT NULL,
    is_correct       BOOLEAN      NOT NULL DEFAULT FALSE,
    misconception_id BIGINT,
    position         INT          NOT NULL DEFAULT 0,
    times_chosen     INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_option_item          FOREIGN KEY (item_id)          REFERENCES items (id)          ON DELETE CASCADE,
    CONSTRAINT fk_option_misconception FOREIGN KEY (misconception_id) REFERENCES misconceptions (id) ON DELETE SET NULL,
    CONSTRAINT ck_option_correctness   CHECK (NOT (is_correct AND misconception_id IS NOT NULL))
);

COMMENT ON TABLE item_options IS 'Answer options. Distractors should carry a misconception_id — that mapping is the entire diagnosis mechanism, and an untagged distractor teaches the system nothing.';

CREATE INDEX idx_options_item          ON item_options (item_id, position);
CREATE INDEX idx_options_misconception ON item_options (misconception_id);

-- -----------------------------------------------------------------------------
-- assessments
-- -----------------------------------------------------------------------------
CREATE TABLE assessments (
    id              BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id         BIGINT      NOT NULL,
    type            VARCHAR(20) NOT NULL,
    skill_id        BIGINT,
    -- No foreign key by design even in the monolith's original: career-
    -- service owns career_roles(id), and this column was always a plain
    -- BIGINT with no REFERENCES clause.
    career_role_id  BIGINT,
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    items_served    INT         NOT NULL DEFAULT 0,
    items_correct   INT         NOT NULL DEFAULT 0,
    score           NUMERIC(5,2),
    termination_reason VARCHAR(30),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT fk_assess_skill  FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE SET NULL,
    CONSTRAINT ck_assess_type   CHECK (type IN ('DIAGNOSTIC', 'PRACTICE', 'CHECKPOINT', 'ARENA')),
    CONSTRAINT ck_assess_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_assess_term   CHECK (termination_reason IS NULL OR termination_reason IN
        ('CONFIDENCE_REACHED', 'ITEM_CAP', 'TIME_LIMIT', 'ABANDONED', 'NO_ITEMS_AVAILABLE'))
);

CREATE INDEX idx_assess_user_type ON assessments (user_id, type, started_at DESC);
CREATE INDEX idx_assess_open      ON assessments (user_id) WHERE status = 'IN_PROGRESS';

-- A learner can only ever have one assessment actively in flight — see
-- monolith V13 for the concurrency bug this partial unique index closes.
CREATE UNIQUE INDEX ux_assessments_one_in_progress_per_user
    ON assessments (user_id)
    WHERE status = 'IN_PROGRESS';

-- -----------------------------------------------------------------------------
-- assessment_items — what was served, in what order (drives exposure control)
-- -----------------------------------------------------------------------------
CREATE TABLE assessment_items (
    id            BIGSERIAL PRIMARY KEY,
    assessment_id BIGINT      NOT NULL,
    item_id       BIGINT      NOT NULL,
    position      INT         NOT NULL,
    selection_info NUMERIC(8,5),
    served_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_assessment_item  UNIQUE (assessment_id, item_id),
    CONSTRAINT fk_ai_assessment    FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_item          FOREIGN KEY (item_id)       REFERENCES items (id)       ON DELETE CASCADE
);
CREATE INDEX idx_ai_assessment ON assessment_items (assessment_id, position);

-- -----------------------------------------------------------------------------
-- responses — append-only. The audit trail behind every passport number.
-- -----------------------------------------------------------------------------
CREATE TABLE responses (
    id                 BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id            BIGINT       NOT NULL,
    item_id            BIGINT       NOT NULL,
    assessment_id      BIGINT,
    selected_option_id BIGINT,
    free_text_answer   TEXT,
    is_correct         BOOLEAN      NOT NULL,
    response_time_ms   INT,

    ability_before     NUMERIC(6,4),
    ability_after      NUMERIC(6,4),
    mastery_before     NUMERIC(5,4),
    mastery_after      NUMERIC(5,4),
    misconception_id   BIGINT,

    answered_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_resp_item          FOREIGN KEY (item_id)            REFERENCES items (id)          ON DELETE CASCADE,
    CONSTRAINT fk_resp_assessment    FOREIGN KEY (assessment_id)      REFERENCES assessments (id)    ON DELETE SET NULL,
    CONSTRAINT fk_resp_option        FOREIGN KEY (selected_option_id) REFERENCES item_options (id)   ON DELETE SET NULL,
    CONSTRAINT fk_resp_misconception FOREIGN KEY (misconception_id)   REFERENCES misconceptions (id) ON DELETE SET NULL
);

COMMENT ON TABLE responses IS
'Append-only. Rows are never updated or deleted: this is the evidence trail that makes every skill percentage defensible, and a mutable audit record is not an audit record.';

CREATE INDEX idx_resp_user_time      ON responses (user_id, answered_at DESC);
CREATE INDEX idx_resp_user_item      ON responses (user_id, item_id);
CREATE INDEX idx_resp_item           ON responses (item_id);
CREATE INDEX idx_resp_assessment     ON responses (assessment_id);
CREATE INDEX idx_resp_recent_wrong   ON responses (user_id, answered_at DESC) WHERE NOT is_correct;
CREATE INDEX idx_resp_misconception  ON responses (misconception_id) WHERE misconception_id IS NOT NULL;
