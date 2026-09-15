-- =============================================================================
-- V1 — Career, Path & Passport, this service's own database
--
-- Same approach as every other service's own V1: the final shape these 6
-- tables had already reached (monolith V6 + V14's uppercase literals)
-- collapsed into one clean "service birth" migration.
--
-- Foreign keys NOT reproduced — same reasoning as assessment-service's own
-- V1 header comment:
--   - user_id -> users(id): identity-service's database now.
--   - skill_id (role_skill_requirements, path_steps) -> skills(id):
--     assessment-service's database now. This service keeps a small local
--     mirror instead (see V2, V3).
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
    seniority    VARCHAR(20)  NOT NULL DEFAULT 'JUNIOR',
    icon         VARCHAR(50),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,

    CONSTRAINT uq_career_slug     UNIQUE (slug),
    CONSTRAINT ck_career_seniority CHECK (seniority IN ('INTERN', 'JUNIOR', 'MID', 'SENIOR'))
);
COMMENT ON TABLE career_roles IS 'A career is a target vector of skills. This is what replaces "which course do you want to buy?" with "what are you trying to become?".';

CREATE INDEX idx_career_active ON career_roles (is_active, category);

-- -----------------------------------------------------------------------------
-- role_skill_requirements — the target vector
-- -----------------------------------------------------------------------------
CREATE TABLE role_skill_requirements (
    id               BIGSERIAL PRIMARY KEY,
    career_role_id   BIGINT       NOT NULL,
    -- No foreign key — see this file's header comment.
    skill_id         BIGINT       NOT NULL,
    required_mastery NUMERIC(3,2) NOT NULL DEFAULT 0.70,
    weight           NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    is_core          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_role_skill    UNIQUE (career_role_id, skill_id),
    CONSTRAINT fk_rsr_role      FOREIGN KEY (career_role_id) REFERENCES career_roles (id) ON DELETE CASCADE,
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
    -- No foreign key — see this file's header comment.
    user_id        BIGINT      NOT NULL,
    career_role_id BIGINT      NOT NULL,
    is_primary     BOOLEAN     NOT NULL DEFAULT TRUE,
    target_date    DATE,
    weekly_minutes INT         NOT NULL DEFAULT 300,
    set_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    achieved_at    TIMESTAMPTZ,

    CONSTRAINT uq_learner_goal UNIQUE (user_id, career_role_id),
    CONSTRAINT fk_lcg_role     FOREIGN KEY (career_role_id) REFERENCES career_roles (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_lcg_one_primary ON learner_career_goals (user_id) WHERE is_primary;

-- -----------------------------------------------------------------------------
-- learning_paths
-- -----------------------------------------------------------------------------
CREATE TABLE learning_paths (
    id              BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id         BIGINT      NOT NULL,
    career_role_id  BIGINT      NOT NULL,
    version         INT         NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    generation_reason VARCHAR(40) NOT NULL DEFAULT 'INITIAL',
    total_steps     INT         NOT NULL DEFAULT 0,
    completed_steps INT         NOT NULL DEFAULT 0,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    superseded_at   TIMESTAMPTZ,

    CONSTRAINT uq_path_version UNIQUE (user_id, career_role_id, version),
    CONSTRAINT fk_lp_role      FOREIGN KEY (career_role_id) REFERENCES career_roles (id) ON DELETE CASCADE,
    CONSTRAINT ck_lp_status    CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_lp_reason    CHECK (generation_reason IN
        ('INITIAL', 'DIAGNOSTIC_COMPLETED', 'MASTERY_CHANGED', 'GOAL_CHANGED', 'DECAY_DETECTED', 'MANUAL'))
);

COMMENT ON TABLE learning_paths IS 'Re-planning never mutates a path — it supersedes it with a new version, so the learner''s route history stays intact and auditable.';

CREATE UNIQUE INDEX uq_lp_one_active ON learning_paths (user_id, career_role_id) WHERE status = 'ACTIVE';

-- -----------------------------------------------------------------------------
-- path_steps — rationale is the explainability feature
-- -----------------------------------------------------------------------------
CREATE TABLE path_steps (
    id               BIGSERIAL PRIMARY KEY,
    learning_path_id BIGINT      NOT NULL,
    position         INT         NOT NULL,
    -- No foreign key — see this file's header comment.
    skill_id         BIGINT      NOT NULL,
    activity_type    VARCHAR(20) NOT NULL,
    activity_id      BIGINT,
    status           VARCHAR(20) NOT NULL DEFAULT 'LOCKED',
    est_minutes      INT         NOT NULL DEFAULT 30,
    rationale        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_step_position UNIQUE (learning_path_id, position),
    CONSTRAINT fk_ps_path       FOREIGN KEY (learning_path_id) REFERENCES learning_paths (id) ON DELETE CASCADE,
    CONSTRAINT ck_ps_activity   CHECK (activity_type IN ('LESSON', 'PRACTICE', 'ASSESSMENT', 'PROJECT', 'REVIEW')),
    CONSTRAINT ck_ps_status     CHECK (status IN ('LOCKED', 'AVAILABLE', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'))
);

COMMENT ON COLUMN path_steps.rationale IS
'Written when the step is generated, never reconstructed afterwards. Reconstructing "why" later would produce a plausible-sounding fiction rather than a record — this column is what makes the explanation trustworthy.';

CREATE INDEX idx_ps_path        ON path_steps (learning_path_id, position);
CREATE INDEX idx_ps_skill       ON path_steps (skill_id);
CREATE INDEX idx_ps_available   ON path_steps (learning_path_id) WHERE status IN ('AVAILABLE', 'IN_PROGRESS');
CREATE INDEX idx_ps_rationale   ON path_steps USING GIN (rationale);

-- -----------------------------------------------------------------------------
-- passport_snapshots — shareable, verifiable
-- -----------------------------------------------------------------------------
CREATE TABLE passport_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id         BIGINT       NOT NULL,
    career_role_id  BIGINT,
    snapshot        JSONB        NOT NULL,
    readiness_score NUMERIC(5,2),
    evidence_count  INT          NOT NULL DEFAULT 0,
    share_token     VARCHAR(64),
    share_expires_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_passport_share UNIQUE (share_token),
    CONSTRAINT fk_pass_role      FOREIGN KEY (career_role_id) REFERENCES career_roles (id) ON DELETE SET NULL
);

COMMENT ON TABLE passport_snapshots IS 'An immutable, timestamped view of a learner''s verified skills. share_token lets an employer open it without an account, and every figure drills through to the evidence rows that produced it.';

CREATE INDEX idx_pass_user ON passport_snapshots (user_id, created_at DESC);
