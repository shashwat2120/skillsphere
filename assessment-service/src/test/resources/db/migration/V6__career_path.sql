-- =============================================================================
-- V6 — Career, Path & Passport
-- Careers as target skill vectors, generated learning paths, and the
-- explainability trace that powers "Why this?".
-- =============================================================================

-- -----------------------------------------------------------------------------
-- career_roles
-- -----------------------------------------------------------------------------
CREATE TABLE career_roles (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(150) NOT NULL,
    slug         VARCHAR(170) NOT NULL,
    description  TEXT,
    category     VARCHAR(100),
    seniority    VARCHAR(20)  NOT NULL DEFAULT 'junior',
    icon         VARCHAR(50),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,

    CONSTRAINT uq_career_slug     UNIQUE (slug),
    CONSTRAINT ck_career_seniority CHECK (seniority IN ('intern', 'junior', 'mid', 'senior'))
);
COMMENT ON TABLE career_roles IS 'A career is a target vector of skills. This is what replaces "which course do you want to buy?" with "what are you trying to become?".';

CREATE INDEX idx_career_active ON career_roles (is_active, category);

-- -----------------------------------------------------------------------------
-- role_skill_requirements — the target vector
-- -----------------------------------------------------------------------------
CREATE TABLE role_skill_requirements (
    id               BIGSERIAL PRIMARY KEY,
    career_role_id   BIGINT       NOT NULL,
    skill_id         BIGINT       NOT NULL,
    -- Mastery a candidate needs to be considered ready for this skill
    required_mastery NUMERIC(3,2) NOT NULL DEFAULT 0.70,
    -- How much this skill counts toward overall readiness
    weight           NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    is_core          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_role_skill    UNIQUE (career_role_id, skill_id),
    CONSTRAINT fk_rsr_role      FOREIGN KEY (career_role_id) REFERENCES career_roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_rsr_skill     FOREIGN KEY (skill_id)       REFERENCES skills (id)       ON DELETE CASCADE,
    CONSTRAINT ck_rsr_mastery   CHECK (required_mastery > 0 AND required_mastery <= 1),
    CONSTRAINT ck_rsr_weight    CHECK (weight > 0 AND weight <= 1)
);
COMMENT ON COLUMN role_skill_requirements.is_core IS 'Core skills gate readiness; non-core skills raise the score but never block it.';

CREATE INDEX idx_rsr_skill ON role_skill_requirements (skill_id);

-- -----------------------------------------------------------------------------
-- learner_career_goals
-- -----------------------------------------------------------------------------
CREATE TABLE learner_career_goals (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    career_role_id BIGINT      NOT NULL,
    is_primary     BOOLEAN     NOT NULL DEFAULT TRUE,
    target_date    DATE,
    -- Declared study capacity, used by the path pacer and what-if simulator
    weekly_minutes INT         NOT NULL DEFAULT 300,
    set_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    achieved_at    TIMESTAMPTZ,

    CONSTRAINT uq_learner_goal UNIQUE (user_id, career_role_id),
    CONSTRAINT fk_lcg_user     FOREIGN KEY (user_id)        REFERENCES users (id)        ON DELETE CASCADE,
    CONSTRAINT fk_lcg_role     FOREIGN KEY (career_role_id) REFERENCES career_roles (id) ON DELETE CASCADE
);
-- At most one primary goal per learner
CREATE UNIQUE INDEX uq_lcg_one_primary ON learner_career_goals (user_id) WHERE is_primary;

-- -----------------------------------------------------------------------------
-- learning_paths
-- -----------------------------------------------------------------------------
CREATE TABLE learning_paths (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    career_role_id  BIGINT      NOT NULL,
    version         INT         NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    -- Why this version was produced, for the path history view
    generation_reason VARCHAR(40) NOT NULL DEFAULT 'initial',
    total_steps     INT         NOT NULL DEFAULT 0,
    completed_steps INT         NOT NULL DEFAULT 0,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    superseded_at   TIMESTAMPTZ,

    CONSTRAINT uq_path_version UNIQUE (user_id, career_role_id, version),
    CONSTRAINT fk_lp_user      FOREIGN KEY (user_id)        REFERENCES users (id)        ON DELETE CASCADE,
    CONSTRAINT fk_lp_role      FOREIGN KEY (career_role_id) REFERENCES career_roles (id) ON DELETE CASCADE,
    CONSTRAINT ck_lp_status    CHECK (status IN ('active', 'superseded', 'completed', 'abandoned')),
    CONSTRAINT ck_lp_reason    CHECK (generation_reason IN
        ('initial', 'diagnostic_completed', 'mastery_changed', 'goal_changed', 'decay_detected', 'manual'))
);

COMMENT ON TABLE learning_paths IS 'Re-planning never mutates a path — it supersedes it with a new version, so the learner''s route history stays intact and auditable.';

CREATE UNIQUE INDEX uq_lp_one_active ON learning_paths (user_id, career_role_id) WHERE status = 'active';

-- -----------------------------------------------------------------------------
-- path_steps — rationale is the explainability feature
-- -----------------------------------------------------------------------------
CREATE TABLE path_steps (
    id               BIGSERIAL PRIMARY KEY,
    learning_path_id BIGINT      NOT NULL,
    position         INT         NOT NULL,
    skill_id         BIGINT      NOT NULL,
    activity_type    VARCHAR(20) NOT NULL,
    activity_id      BIGINT,
    status           VARCHAR(20) NOT NULL DEFAULT 'locked',
    est_minutes      INT         NOT NULL DEFAULT 30,

    -- The decision trace, written at generation time. Shape:
    -- { "prerequisites_met": [{skill, mastery}],
    --   "gap": {current, required},
    --   "role_weight": 0.8,
    --   "failed_items": [{item_id, misconception}],
    --   "selected_because": "..." }
    rationale        JSONB       NOT NULL DEFAULT '{}'::jsonb,

    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_step_position UNIQUE (learning_path_id, position),
    CONSTRAINT fk_ps_path       FOREIGN KEY (learning_path_id) REFERENCES learning_paths (id) ON DELETE CASCADE,
    CONSTRAINT fk_ps_skill      FOREIGN KEY (skill_id)         REFERENCES skills (id)         ON DELETE CASCADE,
    CONSTRAINT ck_ps_activity   CHECK (activity_type IN ('lesson', 'practice', 'assessment', 'project', 'review')),
    CONSTRAINT ck_ps_status     CHECK (status IN ('locked', 'available', 'in_progress', 'completed', 'skipped'))
);

COMMENT ON COLUMN path_steps.rationale IS
'Written when the step is generated, never reconstructed afterwards. Reconstructing "why" later would produce a plausible-sounding fiction rather than a record — this column is what makes the explanation trustworthy.';

CREATE INDEX idx_ps_path        ON path_steps (learning_path_id, position);
CREATE INDEX idx_ps_skill       ON path_steps (skill_id);
CREATE INDEX idx_ps_available   ON path_steps (learning_path_id) WHERE status IN ('available', 'in_progress');
CREATE INDEX idx_ps_rationale   ON path_steps USING GIN (rationale);

-- -----------------------------------------------------------------------------
-- passport_snapshots — shareable, verifiable
-- -----------------------------------------------------------------------------
CREATE TABLE passport_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    career_role_id  BIGINT,
    -- Frozen view: skills, mastery values and the evidence ids behind each
    snapshot        JSONB        NOT NULL,
    readiness_score NUMERIC(5,2),
    evidence_count  INT          NOT NULL DEFAULT 0,
    -- Public share link token; null means private
    share_token     VARCHAR(64),
    share_expires_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_passport_share UNIQUE (share_token),
    CONSTRAINT fk_pass_user      FOREIGN KEY (user_id)        REFERENCES users (id)        ON DELETE CASCADE,
    CONSTRAINT fk_pass_role      FOREIGN KEY (career_role_id) REFERENCES career_roles (id) ON DELETE SET NULL
);

COMMENT ON TABLE passport_snapshots IS 'An immutable, timestamped view of a learner''s verified skills. share_token lets an employer open it without an account, and every figure drills through to the evidence rows that produced it.';

CREATE INDEX idx_pass_user ON passport_snapshots (user_id, created_at DESC);
