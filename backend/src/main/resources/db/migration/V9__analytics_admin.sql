-- =============================================================================
-- V9 — Analytics, Admin & Notifications
-- The event-sourced spine, at-risk prediction, admin governance and the
-- notification store.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- learning_events — the event-sourced spine (becomes a Kafka topic in Sprint 6)
-- -----------------------------------------------------------------------------
CREATE TABLE learning_events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(30),
    entity_id   BIGINT,
    course_id   BIGINT,
    skill_id    BIGINT,
    payload     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_le_user   FOREIGN KEY (user_id)   REFERENCES users (id)   ON DELETE CASCADE,
    CONSTRAINT fk_le_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE SET NULL,
    CONSTRAINT fk_le_skill  FOREIGN KEY (skill_id)  REFERENCES skills (id)  ON DELETE SET NULL
);

COMMENT ON TABLE learning_events IS
'Append-only event stream: every meaningful learner action lands here once. In Sprint 5 the analytics jobs read this table directly; in Sprint 6 the same events are published to Kafka and consumed by the analytics service, so the contract does not change when the architecture does.';

CREATE INDEX idx_le_user_time   ON learning_events (user_id, occurred_at DESC);
CREATE INDEX idx_le_type_time   ON learning_events (event_type, occurred_at DESC);
CREATE INDEX idx_le_course_time ON learning_events (course_id, occurred_at DESC);

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

    CONSTRAINT pk_daily_activity PRIMARY KEY (user_id, activity_date),
    CONSTRAINT fk_da_user        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
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
    -- Why this learner was flagged, e.g.
    -- {"inactivity_days":9,"engagement_slope":-0.4,"failure_cluster":"collections",
    --  "days_since_enrol":6,"early_window":true}
    factors      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- 50% of dropouts happen in the first two weeks, so that window is weighted
    is_early_window BOOLEAN   NOT NULL DEFAULT FALSE,
    intervened_at TIMESTAMPTZ,
    intervened_by BIGINT,
    computed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_rs_user      FOREIGN KEY (user_id)       REFERENCES users (id)   ON DELETE CASCADE,
    CONSTRAINT fk_rs_course    FOREIGN KEY (course_id)     REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_rs_intervener FOREIGN KEY (intervened_by) REFERENCES users (id)  ON DELETE SET NULL,
    CONSTRAINT ck_rs_band      CHECK (band IN ('low', 'medium', 'high')),
    CONSTRAINT ck_rs_score     CHECK (score >= 0 AND score <= 1)
);

COMMENT ON COLUMN risk_scores.factors IS 'The flag is never presented without its reasons — an instructor acting on "this learner is at risk" needs to know why before they can intervene usefully.';

CREATE INDEX idx_rs_course_band ON risk_scores (course_id, band, computed_at DESC);
CREATE INDEX idx_rs_open_high   ON risk_scores (computed_at DESC) WHERE band = 'high' AND intervened_at IS NULL;

-- -----------------------------------------------------------------------------
-- item_statistics — periodic psychometric rollup for item analysis
-- -----------------------------------------------------------------------------
CREATE TABLE item_statistics (
    item_id            BIGINT       PRIMARY KEY,
    p_value            NUMERIC(5,4),
    point_biserial     NUMERIC(5,4),
    avg_response_ms    INT,
    -- Share of responses landing on each distractor: {"option_id": 0.31, ...}
    distractor_analysis JSONB       NOT NULL DEFAULT '{}'::jsonb,
    sample_size        INT          NOT NULL DEFAULT 0,
    computed_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_istat_item FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE CASCADE
);
COMMENT ON TABLE item_statistics IS 'Classical test theory alongside the IRT parameters. A low or negative point-biserial flags a broken item — one where stronger learners do worse — which is how the bank stays honest.';

-- -----------------------------------------------------------------------------
-- instructor_applications — the approval queue
-- -----------------------------------------------------------------------------
CREATE TABLE instructor_applications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    motivation    TEXT,
    credentials   TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'pending',
    reviewed_by   BIGINT,
    reviewed_at   TIMESTAMPTZ,
    review_notes  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_ia_user     FOREIGN KEY (user_id)     REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ia_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_ia_status   CHECK (status IN ('pending', 'approved', 'rejected'))
);
CREATE INDEX idx_ia_pending ON instructor_applications (created_at) WHERE status = 'pending';

-- -----------------------------------------------------------------------------
-- admin_audit_log — every admin action, with before/after
-- -----------------------------------------------------------------------------
CREATE TABLE admin_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT       NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    target_type VARCHAR(40)  NOT NULL,
    target_id   BIGINT,
    before_state JSONB,
    after_state  JSONB,
    ip_address  INET,
    reason      VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_aal_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE RESTRICT
);
COMMENT ON TABLE admin_audit_log IS 'Admins hold destructive power — suspending accounts, rewriting the skill graph, changing career requirements. Every such action is recorded with its before and after state, and the log is never deleted.';

CREATE INDEX idx_aal_admin_time ON admin_audit_log (admin_id, created_at DESC);
CREATE INDEX idx_aal_target     ON admin_audit_log (target_type, target_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- platform_settings
-- -----------------------------------------------------------------------------
CREATE TABLE platform_settings (
    key         VARCHAR(100) PRIMARY KEY,
    value       JSONB        NOT NULL,
    description VARCHAR(400),
    updated_by  BIGINT,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_settings_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

INSERT INTO platform_settings (key, value, description) VALUES
    ('diagnostic.max_items',        '25',    'Hard cap on items served in one diagnostic'),
    ('diagnostic.target_se',        '0.30',  'Stop when the ability standard error falls below this'),
    ('mastery.threshold',           '0.80',  'P(mastery) at which a skill counts as mastered'),
    ('confusion.failure_threshold', '3',     'Related failures needed to raise a confusion signal'),
    ('confusion.window_minutes',    '30',    'Time window for counting related failures'),
    ('viva.turns_default',          '5',     'Questions asked in a standard viva'),
    ('viva.pass_threshold',         '0.60',  'Mean turn score required to verify understanding'),
    ('evidence.decay_days',         '365',   'Age at which evidence stops counting at full weight'),
    ('risk.early_window_days',      '14',    'Enrolment window given extra weight in risk scoring');

-- -----------------------------------------------------------------------------
-- notifications
-- -----------------------------------------------------------------------------
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(600),
    link       VARCHAR(500),
    icon       VARCHAR(50),
    priority   VARCHAR(10)  NOT NULL DEFAULT 'normal',
    data       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_notif_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notif_prio  CHECK (priority IN ('low', 'normal', 'high'))
);
CREATE INDEX idx_notif_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_notif_user_all    ON notifications (user_id, created_at DESC);

CREATE TABLE notification_preferences (
    user_id BIGINT      NOT NULL,
    type    VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_notif_prefs PRIMARY KEY (user_id, type, channel),
    CONSTRAINT fk_np_user     FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_np_channel  CHECK (channel IN ('in_app', 'email', 'push'))
);
