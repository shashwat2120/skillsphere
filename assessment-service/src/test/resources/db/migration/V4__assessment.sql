-- =============================================================================
-- V4 — Assessment
-- The measurement layer: a self-calibrating item bank, misconception-tagged
-- distractors, adaptive assessments, and an append-only response ledger.
-- Every number in the skill passport ultimately traces back to `responses`.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- misconceptions — what a wrong answer actually reveals
-- -----------------------------------------------------------------------------
CREATE TABLE misconceptions (
    id                BIGSERIAL PRIMARY KEY,
    skill_id          BIGINT       NOT NULL,
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    remediation_hint  TEXT         NOT NULL,
    -- Lesson that directly addresses this misunderstanding, if one exists
    remediation_lesson_id BIGINT,
    times_observed    INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,

    CONSTRAINT fk_misconception_skill  FOREIGN KEY (skill_id)              REFERENCES skills (id)  ON DELETE CASCADE,
    CONSTRAINT fk_misconception_lesson FOREIGN KEY (remediation_lesson_id) REFERENCES lessons (id) ON DELETE SET NULL
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
    type                VARCHAR(20)  NOT NULL DEFAULT 'mcq',
    explanation         TEXT,

    -- ---- Psychometrics -------------------------------------------------------
    -- Instructor's declared difficulty, used as the Bayesian prior (cold start)
    declared_difficulty NUMERIC(4,2) NOT NULL DEFAULT 0.00,
    -- IRT 2PL parameters, learned from real responses
    difficulty_b        NUMERIC(6,4) NOT NULL DEFAULT 0.0000,
    discrimination_a    NUMERIC(6,4) NOT NULL DEFAULT 1.0000,
    -- Elo rating, updated against learner ratings on every response
    elo_rating          INT          NOT NULL DEFAULT 1200,
    -- True once enough responses exist for the learned values to be trusted
    is_calibrated       BOOLEAN      NOT NULL DEFAULT FALSE,

    times_seen          INT          NOT NULL DEFAULT 0,
    times_correct       INT          NOT NULL DEFAULT 0,
    avg_response_ms     INT,

    author_id           BIGINT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'draft',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT fk_items_skill   FOREIGN KEY (skill_id)  REFERENCES skills (id) ON DELETE CASCADE,
    CONSTRAINT fk_items_author  FOREIGN KEY (author_id) REFERENCES users (id)  ON DELETE SET NULL,
    CONSTRAINT ck_items_type    CHECK (type IN ('mcq', 'multi_select', 'numeric', 'short_text', 'code')),
    CONSTRAINT ck_items_status  CHECK (status IN ('draft', 'active', 'retired')),
    CONSTRAINT ck_items_counts  CHECK (times_correct >= 0 AND times_correct <= times_seen),
    CONSTRAINT ck_items_discrim CHECK (discrimination_a > 0)
);

COMMENT ON COLUMN items.declared_difficulty IS 'The cold-start answer: the author states a difficulty, it seeds the prior, and difficulty_b/elo_rating then correct it from real response data. Kept separately so the prior and the learned value are both visible.';
COMMENT ON COLUMN items.discrimination_a IS 'IRT 2PL discrimination — how sharply this item separates learners near its difficulty. Higher means more informative.';

CREATE INDEX idx_items_skill_active ON items (skill_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_items_difficulty   ON items (skill_id, difficulty_b) WHERE status = 'active';
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
    -- A correct option cannot also encode a misconception
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
    user_id         BIGINT      NOT NULL,
    type            VARCHAR(20) NOT NULL,
    skill_id        BIGINT,
    career_role_id  BIGINT,
    status          VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    items_served    INT         NOT NULL DEFAULT 0,
    items_correct   INT         NOT NULL DEFAULT 0,
    score           NUMERIC(5,2),
    -- Why the adaptive engine stopped: confidence reached, cap hit, or abandoned
    termination_reason VARCHAR(30),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT fk_assess_user   FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_assess_skill  FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE SET NULL,
    CONSTRAINT ck_assess_type   CHECK (type IN ('diagnostic', 'practice', 'checkpoint', 'arena')),
    CONSTRAINT ck_assess_status CHECK (status IN ('in_progress', 'completed', 'abandoned')),
    CONSTRAINT ck_assess_term   CHECK (termination_reason IS NULL OR termination_reason IN
        ('confidence_reached', 'item_cap', 'time_limit', 'abandoned', 'no_items_available'))
);

CREATE INDEX idx_assess_user_type ON assessments (user_id, type, started_at DESC);
CREATE INDEX idx_assess_open      ON assessments (user_id) WHERE status = 'in_progress';

-- -----------------------------------------------------------------------------
-- assessment_items — what was served, in what order (drives exposure control)
-- -----------------------------------------------------------------------------
CREATE TABLE assessment_items (
    id            BIGSERIAL PRIMARY KEY,
    assessment_id BIGINT      NOT NULL,
    item_id       BIGINT      NOT NULL,
    position      INT         NOT NULL,
    -- Expected information gain at selection time — lets us audit the engine's choices
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
    user_id            BIGINT       NOT NULL,
    item_id            BIGINT       NOT NULL,
    assessment_id      BIGINT,
    selected_option_id BIGINT,
    free_text_answer   TEXT,
    is_correct         BOOLEAN      NOT NULL,
    response_time_ms   INT,

    -- Ability snapshot either side of this response, so the engine is auditable
    ability_before     NUMERIC(6,4),
    ability_after      NUMERIC(6,4),
    mastery_before     NUMERIC(5,4),
    mastery_after      NUMERIC(5,4),
    -- Misconception inferred from the chosen distractor
    misconception_id   BIGINT,

    answered_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_resp_user          FOREIGN KEY (user_id)            REFERENCES users (id)          ON DELETE CASCADE,
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
-- Powers confusion detection: recent wrong answers for a learner
CREATE INDEX idx_resp_recent_wrong   ON responses (user_id, answered_at DESC) WHERE NOT is_correct;
CREATE INDEX idx_resp_misconception  ON responses (misconception_id) WHERE misconception_id IS NOT NULL;
