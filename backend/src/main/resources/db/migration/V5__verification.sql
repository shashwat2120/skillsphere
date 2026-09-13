-- =============================================================================
-- V5 — Verification
-- The differentiator: projects, the process ledger, the AI viva, instructor
-- review, and the immutable evidence store the skill passport is derived from.
--
-- Design principle: we do not detect AI. We record evidence of real work.
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
    instructor_id  BIGINT,
    level_band     VARCHAR(20)  NOT NULL DEFAULT 'intermediate',
    est_minutes    INT          NOT NULL DEFAULT 120,
    -- Rubric criteria as structured JSON: [{key, label, weight, descriptors[]}]
    rubric         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- Whether a viva is mandatory before evidence is issued
    requires_viva  BOOLEAN      NOT NULL DEFAULT TRUE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'draft',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,

    CONSTRAINT uq_projects_slug      UNIQUE (slug),
    CONSTRAINT fk_projects_instructor FOREIGN KEY (instructor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_projects_status     CHECK (status IN ('draft', 'published', 'archived')),
    CONSTRAINT ck_projects_band       CHECK (level_band IN ('foundational', 'intermediate', 'advanced', 'expert'))
);
CREATE INDEX idx_projects_status ON projects (status) WHERE deleted_at IS NULL;

CREATE TABLE project_skills (
    project_id BIGINT       NOT NULL,
    skill_id   BIGINT       NOT NULL,
    weight     NUMERIC(3,2) NOT NULL DEFAULT 1.00,

    CONSTRAINT pk_project_skills PRIMARY KEY (project_id, skill_id),
    CONSTRAINT fk_ps_project    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_ps_skill      FOREIGN KEY (skill_id)   REFERENCES skills (id)   ON DELETE CASCADE,
    CONSTRAINT ck_ps_weight     CHECK (weight > 0 AND weight <= 1)
);
CREATE INDEX idx_project_skills_skill ON project_skills (skill_id);

-- -----------------------------------------------------------------------------
-- submissions
-- -----------------------------------------------------------------------------
CREATE TABLE submissions (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    project_id    BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'draft',
    content       TEXT,
    repo_url      VARCHAR(500),
    artefact_url  VARCHAR(500),
    attempt_no    INT         NOT NULL DEFAULT 1,

    -- Derived process signals, computed when the submission is sealed.
    -- These describe the *shape* of the work, not a verdict about AI.
    active_minutes      INT,
    draft_count         INT NOT NULL DEFAULT 0,
    large_paste_count   INT NOT NULL DEFAULT 0,
    first_activity_at   TIMESTAMPTZ,

    submitted_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT fk_sub_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_sub_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_sub_status  CHECK (status IN
        ('draft', 'submitted', 'in_viva', 'awaiting_review', 'verified', 'not_verified'))
);

COMMENT ON COLUMN submissions.status IS
'verified = the learner defended this work successfully and evidence was issued. not_verified = the submission was accepted but understanding was not demonstrated — the work is not rejected, it simply produces no evidence.';

CREATE INDEX idx_sub_user_project ON submissions (user_id, project_id, attempt_no DESC);
CREATE INDEX idx_sub_status       ON submissions (status, submitted_at DESC);
CREATE INDEX idx_sub_review_queue ON submissions (submitted_at) WHERE status = 'awaiting_review';

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
        ('session_start', 'session_end', 'draft_saved', 'edit', 'paste',
         'large_paste', 'run', 'test_pass', 'test_fail', 'idle', 'ai_consulted'))
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
        ('brainstorming', 'explanation', 'debugging', 'code_generation', 'review', 'writing', 'none')),
    CONSTRAINT ck_aiud_extent  CHECK (extent IN ('none', 'minor', 'moderate', 'substantial'))
);

COMMENT ON TABLE ai_usage_declarations IS
'Learners declare AI use rather than being forbidden it. Banning is unenforceable and poor preparation for real work; what gets assessed is their judgement about the AI''s output, probed in the viva.';

-- -----------------------------------------------------------------------------
-- viva_sessions — the scalable oral defence
-- -----------------------------------------------------------------------------
CREATE TABLE viva_sessions (
    id                BIGSERIAL PRIMARY KEY,
    submission_id     BIGINT      NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'pending',
    verdict           VARCHAR(20),
    overall_score     NUMERIC(5,2),
    turns_planned     INT         NOT NULL DEFAULT 5,
    turns_completed   INT         NOT NULL DEFAULT 0,
    -- Which model produced the questions, for reproducibility
    generator_model   VARCHAR(100),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_viva_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT ck_viva_status     CHECK (status IN ('pending', 'in_progress', 'completed', 'abandoned')),
    CONSTRAINT ck_viva_verdict    CHECK (verdict IS NULL OR verdict IN ('verified', 'not_verified', 'inconclusive'))
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
    -- Anchor back into the learner's own artefact: line ref, section, decision
    question_anchor  VARCHAR(300),
    question_intent  VARCHAR(50),
    skill_id         BIGINT,
    answer           TEXT,
    -- Structured grading: {reasoning, criteria_met[], confidence, flags[]}
    evaluation       JSONB,
    score            NUMERIC(5,2),
    time_limit_sec   INT         NOT NULL DEFAULT 180,
    asked_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at      TIMESTAMPTZ,

    CONSTRAINT uq_viva_turn_position UNIQUE (viva_session_id, position),
    CONSTRAINT fk_vt_session FOREIGN KEY (viva_session_id) REFERENCES viva_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_vt_skill   FOREIGN KEY (skill_id)        REFERENCES skills (id)        ON DELETE SET NULL,
    CONSTRAINT ck_vt_intent  CHECK (question_intent IS NULL OR question_intent IN
        ('design_choice', 'trade_off', 'edge_case', 'alternative', 'failure_mode', 'concept_check'))
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
    reviewer_id   BIGINT      NOT NULL,
    rubric_scores JSONB       NOT NULL DEFAULT '{}'::jsonb,
    comments      TEXT,
    decision      VARCHAR(20) NOT NULL,
    reviewed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_ir_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT fk_ir_reviewer   FOREIGN KEY (reviewer_id)   REFERENCES users (id)       ON DELETE RESTRICT,
    CONSTRAINT ck_ir_decision   CHECK (decision IN ('approved', 'rejected', 'revisions_requested'))
);
CREATE INDEX idx_ir_submission ON instructor_reviews (submission_id);
CREATE INDEX idx_ir_reviewer   ON instructor_reviews (reviewer_id, reviewed_at DESC);

-- -----------------------------------------------------------------------------
-- evidence — immutable. The skill passport is derived from this and nothing else.
-- -----------------------------------------------------------------------------
CREATE TABLE evidence (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    skill_id       BIGINT       NOT NULL,
    evidence_type  VARCHAR(20)  NOT NULL,
    source_type    VARCHAR(30)  NOT NULL,
    source_id      BIGINT       NOT NULL,
    -- Contribution of this evidence to the skill claim
    weight         NUMERIC(4,3) NOT NULL DEFAULT 1.000,
    score          NUMERIC(5,2),
    -- Null for machine-issued evidence; set when a human signed it off
    verified_by    BIGINT,
    verified_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Evidence ages: older evidence contributes less to a current claim
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_ev_user     FOREIGN KEY (user_id)     REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_ev_skill    FOREIGN KEY (skill_id)    REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT fk_ev_verifier FOREIGN KEY (verified_by) REFERENCES users (id)  ON DELETE SET NULL,
    CONSTRAINT ck_ev_type     CHECK (evidence_type IN ('assessment', 'project', 'viva', 'instructor', 'arena')),
    CONSTRAINT ck_ev_source   CHECK (source_type IN
        ('assessment', 'submission', 'viva_session', 'instructor_review', 'arena')),
    CONSTRAINT ck_ev_weight   CHECK (weight > 0 AND weight <= 1),
    CONSTRAINT uq_ev_source   UNIQUE (source_type, source_id, skill_id)
);

COMMENT ON TABLE evidence IS
'Immutable record of a demonstrated skill. The passport is computed from these rows, never stored as a number a learner can influence — that derivation is exactly what makes a claim verifiable rather than self-reported.';

CREATE INDEX idx_ev_user_skill ON evidence (user_id, skill_id);
CREATE INDEX idx_ev_user_time  ON evidence (user_id, verified_at DESC);
CREATE INDEX idx_ev_type       ON evidence (evidence_type);
