-- =============================================================================
-- V1 — Verification, this service's own database
--
-- Same approach as identity-service's and assessment-service's own V1: the
-- final shape these 8 tables had already reached in the shared database
-- (monolith V5 + V15's uppercase literals) collapsed into one clean
-- "service birth" migration.
--
-- Foreign keys NOT reproduced here, same reasoning as assessment-service's
-- own V1 header comment:
--   - user_id / reviewer_id / verified_by -> users(id): identity-service's
--     database now.
--   - skill_id (project_skills, viva_turns, evidence) -> skills(id):
--     assessment-service's database now. This service keeps a small local
--     mirror instead (see V2) — read-only, kept current via Kafka, not a
--     foreign key relationship, since a real FK cannot cross a database.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- projects
-- -----------------------------------------------------------------------------
CREATE TABLE projects (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    slug           VARCHAR(220) NOT NULL,
    description    TEXT         NOT NULL,
    brief          TEXT,
    -- No foreign key — see this file's header comment.
    instructor_id  BIGINT,
    level_band     VARCHAR(20)  NOT NULL DEFAULT 'INTERMEDIATE',
    est_minutes    INT          NOT NULL DEFAULT 120,
    rubric         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    requires_viva  BOOLEAN      NOT NULL DEFAULT TRUE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,

    CONSTRAINT uq_projects_slug   UNIQUE (slug),
    CONSTRAINT ck_projects_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_projects_band   CHECK (level_band IN ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
);
CREATE INDEX idx_projects_status ON projects (status) WHERE deleted_at IS NULL;

CREATE TABLE project_skills (
    project_id BIGINT       NOT NULL,
    skill_id   BIGINT       NOT NULL,
    weight     NUMERIC(3,2) NOT NULL DEFAULT 1.00,

    CONSTRAINT pk_project_skills PRIMARY KEY (project_id, skill_id),
    CONSTRAINT fk_ps_project    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_ps_weight     CHECK (weight > 0 AND weight <= 1)
);
CREATE INDEX idx_project_skills_skill ON project_skills (skill_id);

-- -----------------------------------------------------------------------------
-- submissions
-- -----------------------------------------------------------------------------
CREATE TABLE submissions (
    id            BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id       BIGINT      NOT NULL,
    project_id    BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    content       TEXT,
    repo_url      VARCHAR(500),
    artefact_url  VARCHAR(500),
    attempt_no    INT         NOT NULL DEFAULT 1,

    active_minutes      INT,
    draft_count         INT NOT NULL DEFAULT 0,
    large_paste_count   INT NOT NULL DEFAULT 0,
    first_activity_at   TIMESTAMPTZ,

    submitted_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT fk_sub_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_sub_status  CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'IN_VIVA', 'AWAITING_REVIEW', 'VERIFIED', 'NOT_VERIFIED'))
);

COMMENT ON COLUMN submissions.status IS
'VERIFIED = the learner defended this work successfully and evidence was issued. NOT_VERIFIED = the submission was accepted but understanding was not demonstrated — the work is not rejected, it simply produces no evidence.';

CREATE INDEX idx_sub_user_project ON submissions (user_id, project_id, attempt_no DESC);
CREATE INDEX idx_sub_status       ON submissions (status, submitted_at DESC);
CREATE INDEX idx_sub_review_queue ON submissions (submitted_at) WHERE status = 'AWAITING_REVIEW';

-- -----------------------------------------------------------------------------
-- process_events — the process ledger. High volume, append-only.
-- -----------------------------------------------------------------------------
CREATE TABLE process_events (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL,
    event_type    VARCHAR(30) NOT NULL,
    payload       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_pe_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT ck_pe_type CHECK (event_type IN
        ('SESSION_START', 'SESSION_END', 'DRAFT_SAVED', 'EDIT', 'PASTE',
         'LARGE_PASTE', 'RUN', 'TEST_PASS', 'TEST_FAIL', 'IDLE', 'AI_CONSULTED'))
);

COMMENT ON TABLE process_events IS
'The process ledger. Records how the work happened — drafts, edits, runs, pauses — so the learning trajectory is assessable, not only the final artefact. Append-only and never edited.';

CREATE INDEX idx_pe_submission_time ON process_events (submission_id, occurred_at);
CREATE INDEX idx_pe_type            ON process_events (submission_id, event_type);

-- -----------------------------------------------------------------------------
-- ai_usage_declarations — declared, not banned
-- -----------------------------------------------------------------------------
CREATE TABLE ai_usage_declarations (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL,
    tool_used     VARCHAR(100),
    purpose       VARCHAR(50) NOT NULL,
    extent        VARCHAR(20) NOT NULL,
    detail        TEXT,
    declared_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_aiud_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT ck_aiud_purpose CHECK (purpose IN
        ('BRAINSTORMING', 'EXPLANATION', 'DEBUGGING', 'CODE_GENERATION', 'REVIEW', 'WRITING', 'NONE')),
    CONSTRAINT ck_aiud_extent  CHECK (extent IN ('NONE', 'MINOR', 'MODERATE', 'SUBSTANTIAL'))
);

COMMENT ON TABLE ai_usage_declarations IS
'Learners declare AI use rather than being forbidden it. Banning is unenforceable and poor preparation for real work; what gets assessed is their judgement about the AI''s output, probed in the viva.';

-- -----------------------------------------------------------------------------
-- viva_sessions — the scalable oral defence
-- -----------------------------------------------------------------------------
CREATE TABLE viva_sessions (
    id                BIGSERIAL PRIMARY KEY,
    submission_id     BIGINT      NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verdict           VARCHAR(20),
    overall_score     NUMERIC(5,2),
    turns_planned     INT         NOT NULL DEFAULT 5,
    turns_completed   INT         NOT NULL DEFAULT 0,
    generator_model   VARCHAR(100),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,

    CONSTRAINT fk_viva_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT ck_viva_status     CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_viva_verdict    CHECK (verdict IS NULL OR verdict IN ('VERIFIED', 'NOT_VERIFIED', 'INCONCLUSIVE'))
);
CREATE INDEX idx_viva_submission ON viva_sessions (submission_id);
CREATE INDEX idx_viva_status     ON viva_sessions (status);

-- -----------------------------------------------------------------------------
-- viva_turns — one question, one answer, one evaluation
-- -----------------------------------------------------------------------------
CREATE TABLE viva_turns (
    id               BIGSERIAL PRIMARY KEY,
    viva_session_id  BIGINT      NOT NULL,
    position         INT         NOT NULL,
    question         TEXT        NOT NULL,
    question_anchor  VARCHAR(300),
    question_intent  VARCHAR(50),
    -- No foreign key — see this file's header comment.
    skill_id         BIGINT,
    answer           TEXT,
    evaluation       JSONB,
    score            NUMERIC(5,2),
    time_limit_sec   INT         NOT NULL DEFAULT 180,
    asked_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at      TIMESTAMPTZ,

    CONSTRAINT uq_viva_turn_position UNIQUE (viva_session_id, position),
    CONSTRAINT fk_vt_session FOREIGN KEY (viva_session_id) REFERENCES viva_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_vt_intent  CHECK (question_intent IS NULL OR question_intent IN
        ('DESIGN_CHOICE', 'TRADE_OFF', 'EDGE_CASE', 'ALTERNATIVE', 'FAILURE_MODE', 'CONCEPT_CHECK'))
);

COMMENT ON TABLE viva_turns IS
'Questions are generated from the learner''s own submission and cannot exist before it is made, which is what makes the defence impossible to outsource. question_anchor records exactly which part of their work is being probed.';

CREATE INDEX idx_vt_session ON viva_turns (viva_session_id, position);

-- -----------------------------------------------------------------------------
-- instructor_reviews — human sign-off
-- -----------------------------------------------------------------------------
CREATE TABLE instructor_reviews (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL,
    -- No foreign key — see this file's header comment.
    reviewer_id   BIGINT      NOT NULL,
    rubric_scores JSONB       NOT NULL DEFAULT '{}'::jsonb,
    comments      TEXT,
    decision      VARCHAR(20) NOT NULL,
    reviewed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_ir_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT ck_ir_decision   CHECK (decision IN ('APPROVED', 'REJECTED', 'REVISIONS_REQUESTED'))
);
CREATE INDEX idx_ir_submission ON instructor_reviews (submission_id);
CREATE INDEX idx_ir_reviewer   ON instructor_reviews (reviewer_id, reviewed_at DESC);

-- -----------------------------------------------------------------------------
-- evidence — immutable. The skill passport is derived from this and nothing else.
-- -----------------------------------------------------------------------------
CREATE TABLE evidence (
    id             BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id        BIGINT       NOT NULL,
    -- No foreign key — see this file's header comment.
    skill_id       BIGINT       NOT NULL,
    evidence_type  VARCHAR(20)  NOT NULL,
    source_type    VARCHAR(30)  NOT NULL,
    source_id      BIGINT       NOT NULL,
    weight         NUMERIC(4,3) NOT NULL DEFAULT 1.000,
    score          NUMERIC(5,2),
    -- No foreign key — see this file's header comment.
    verified_by    BIGINT,
    verified_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_ev_type     CHECK (evidence_type IN ('ASSESSMENT', 'PROJECT', 'VIVA', 'INSTRUCTOR', 'ARENA')),
    CONSTRAINT ck_ev_source   CHECK (source_type IN
        ('ASSESSMENT', 'SUBMISSION', 'VIVA_SESSION', 'INSTRUCTOR_REVIEW', 'ARENA')),
    CONSTRAINT ck_ev_weight   CHECK (weight > 0 AND weight <= 1),
    CONSTRAINT uq_ev_source   UNIQUE (source_type, source_id, skill_id)
);

COMMENT ON TABLE evidence IS
'Immutable record of a demonstrated skill. The passport is computed from these rows, never stored as a number a learner can influence — that derivation is exactly what makes a claim verifiable rather than self-reported.';

CREATE INDEX idx_ev_user_skill ON evidence (user_id, skill_id);
CREATE INDEX idx_ev_user_time  ON evidence (user_id, verified_at DESC);
CREATE INDEX idx_ev_type       ON evidence (evidence_type);
