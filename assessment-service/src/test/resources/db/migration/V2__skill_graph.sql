-- =============================================================================
-- V2 — Skill Graph
-- The heart of the product: skills as nodes, prerequisites as edges (a DAG),
-- and the per-learner mastery model that every other module reads from.
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
    level_band    VARCHAR(20)  NOT NULL DEFAULT 'intermediate',
    -- Estimated study time, used for path pacing and the what-if simulator
    est_minutes   INT          NOT NULL DEFAULT 60,
    -- How fast unused mastery erodes (per day). 0 = never decays.
    decay_rate    NUMERIC(6,5) NOT NULL DEFAULT 0.00200,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT uq_skills_slug     UNIQUE (slug),
    CONSTRAINT fk_skills_category FOREIGN KEY (category_id) REFERENCES skill_categories (id) ON DELETE SET NULL,
    CONSTRAINT ck_skills_band     CHECK (level_band IN ('foundational', 'intermediate', 'advanced', 'expert')),
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
    -- 1.00 = hard gate, lower values = helpful but not blocking
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
-- -----------------------------------------------------------------------------
CREATE TABLE learner_skill_state (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT        NOT NULL,
    skill_id             BIGINT        NOT NULL,

    -- IRT ability on the logit scale, typically -3.0 .. +3.0
    ability_theta        NUMERIC(6,4)  NOT NULL DEFAULT 0.0000,
    -- Standard error of the ability estimate; shrinks as evidence accumulates
    ability_se           NUMERIC(6,4)  NOT NULL DEFAULT 1.0000,
    -- Bayesian Knowledge Tracing P(mastered), 0..1
    mastery_probability  NUMERIC(5,4)  NOT NULL DEFAULT 0.0500,
    -- Elo rating, kept alongside IRT as a fast, robust secondary estimate
    elo_rating           INT           NOT NULL DEFAULT 1200,

    attempts_count       INT           NOT NULL DEFAULT 0,
    correct_count        INT           NOT NULL DEFAULT 0,
    -- Mastery at its peak, so decay can be shown against a high-water mark
    peak_mastery         NUMERIC(5,4)  NOT NULL DEFAULT 0.0000,

    first_seen_at        TIMESTAMPTZ,
    last_practiced_at    TIMESTAMPTZ,
    mastered_at          TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_learner_skill      UNIQUE (user_id, skill_id),
    CONSTRAINT fk_lss_user           FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_lss_skill          FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT ck_lss_mastery        CHECK (mastery_probability >= 0 AND mastery_probability <= 1),
    CONSTRAINT ck_lss_counts         CHECK (correct_count >= 0 AND correct_count <= attempts_count)
);

COMMENT ON TABLE  learner_skill_state IS 'One row per learner per touched skill. The single most written-to and read-from table in the system — every answered item updates it, and every path decision reads it.';
COMMENT ON COLUMN learner_skill_state.ability_se IS 'Standard error of theta. Falls as evidence accumulates; the diagnostic stops when it drops below the confidence threshold.';

CREATE INDEX idx_lss_user            ON learner_skill_state (user_id);
CREATE INDEX idx_lss_skill           ON learner_skill_state (skill_id);
CREATE INDEX idx_lss_user_mastery    ON learner_skill_state (user_id, mastery_probability DESC);
-- Finds skills whose mastery is eroding and needs a refresher
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

    CONSTRAINT fk_smh_user    FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_smh_skill   FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT ck_smh_trigger CHECK (trigger_type IN
        ('response', 'assessment', 'project', 'viva', 'instructor', 'decay', 'initial'))
);

COMMENT ON TABLE skill_mastery_history IS 'Append-only ledger of every mastery change. Never updated or deleted — it is the evidence behind the progress charts and the decay curve.';

CREATE INDEX idx_smh_user_skill_time ON skill_mastery_history (user_id, skill_id, recorded_at DESC);
