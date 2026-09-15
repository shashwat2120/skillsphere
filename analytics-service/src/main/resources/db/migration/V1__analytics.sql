-- =============================================================================
-- V1 — Analytics, this service's own database
--
-- Same approach as every other service's own V1: the final shape these 3
-- tables had already reached (monolith V9 + V17's correct column/uppercase
-- literal) collapsed into one clean "service birth" migration.
--
-- Foreign keys NOT reproduced — same reasoning as assessment-service's own
-- V1 header comment: user_id/intervened_by -> users(id) is identity-
-- service's database now, course_id -> courses(id) is content-service's.
-- Neither is mirrored: this service never resolves a course or user's name
-- from these ids, it only stores and returns them as opaque references for
-- the frontend (which already holds the learner/course context) to
-- interpret — unlike skill_id in the rest of this split, nothing here ever
-- turns course_id into a display name.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- learning_events — the event-sourced spine
-- -----------------------------------------------------------------------------
CREATE TABLE learning_events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(30),
    entity_id   BIGINT,
    course_id   BIGINT,
    skill_id    BIGINT,
    correct     BOOLEAN,
    payload     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE learning_events IS
'Append-only event stream: every meaningful learner action lands here once, consumed from Kafka (assessment-events) rather than written in-process.';

CREATE INDEX idx_le_user_time   ON learning_events (user_id, occurred_at DESC);
CREATE INDEX idx_le_type_time   ON learning_events (event_type, occurred_at DESC);
CREATE INDEX idx_le_course_time ON learning_events (course_id, occurred_at DESC);
CREATE INDEX idx_le_correct     ON learning_events (occurred_at) WHERE correct IS NOT NULL;

-- -----------------------------------------------------------------------------
-- daily_activity — rollup that keeps dashboards fast
-- -----------------------------------------------------------------------------
CREATE TABLE daily_activity (
    user_id           BIGINT      NOT NULL,
    activity_date     DATE        NOT NULL,
    minutes_active    INT         NOT NULL DEFAULT 0,
    items_answered    INT         NOT NULL DEFAULT 0,
    items_correct     INT         NOT NULL DEFAULT 0,
    lessons_completed INT         NOT NULL DEFAULT 0,
    xp_earned         INT         NOT NULL DEFAULT 0,
    sessions_count    INT         NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_daily_activity PRIMARY KEY (user_id, activity_date)
);
COMMENT ON TABLE daily_activity IS 'Pre-aggregated per learner per day. Cohort dashboards and streak calculations read this instead of scanning learning_events.';

CREATE INDEX idx_da_date ON daily_activity (activity_date DESC);

-- -----------------------------------------------------------------------------
-- risk_scores — at-risk prediction with stated reasons
-- -----------------------------------------------------------------------------
CREATE TABLE risk_scores (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    course_id    BIGINT,
    score        NUMERIC(5,4) NOT NULL,
    band         VARCHAR(10)  NOT NULL,
    factors      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    is_early_window BOOLEAN   NOT NULL DEFAULT FALSE,
    intervened_at TIMESTAMPTZ,
    intervened_by BIGINT,
    computed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_rs_band      CHECK (band IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_rs_score     CHECK (score >= 0 AND score <= 1)
);

COMMENT ON COLUMN risk_scores.factors IS 'The flag is never presented without its reasons — an instructor acting on "this learner is at risk" needs to know why before they can intervene usefully.';

CREATE INDEX idx_rs_course_band ON risk_scores (course_id, band, computed_at DESC);
CREATE INDEX idx_rs_open_high   ON risk_scores (computed_at DESC) WHERE band = 'HIGH' AND intervened_at IS NULL;
