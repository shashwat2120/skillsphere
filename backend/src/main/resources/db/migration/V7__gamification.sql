-- =============================================================================
-- V7 — Gamification
-- XP, levels, streaks, badges, quests and seasonal leaderboards.
--
-- Design principle: gamification rewards *verified* progress, never raw
-- activity. XP is earned when evidence is issued, a skill is mastered, or a
-- viva is passed — never for merely opening a page. If XP could be farmed by
-- clicking, the leaderboard would measure clicking, and the whole credibility
-- of the passport would leak into the game layer.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- user_progression — one row per learner, the gamification summary
-- -----------------------------------------------------------------------------
CREATE TABLE user_progression (
    user_id            BIGINT      PRIMARY KEY,
    total_xp           BIGINT      NOT NULL DEFAULT 0,
    level              INT         NOT NULL DEFAULT 1,
    xp_into_level      INT         NOT NULL DEFAULT 0,

    current_streak     INT         NOT NULL DEFAULT 0,
    longest_streak     INT         NOT NULL DEFAULT 0,
    last_activity_date DATE,
    -- Streak insurance: lets a learner miss a day without losing everything
    streak_freezes     INT         NOT NULL DEFAULT 2,

    skills_mastered    INT         NOT NULL DEFAULT 0,
    projects_verified  INT         NOT NULL DEFAULT 0,
    vivas_passed       INT         NOT NULL DEFAULT 0,
    badges_earned      INT         NOT NULL DEFAULT 0,

    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_progression_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_progression_level CHECK (level >= 1),
    CONSTRAINT ck_progression_streak CHECK (current_streak >= 0 AND longest_streak >= current_streak)
);

COMMENT ON COLUMN user_progression.streak_freezes IS
'Deliberate anti-punishment mechanic. Streak loss is a known cause of abandonment, and 50% of dropouts happen in the first two weeks — a freeze absorbs one missed day instead of erasing weeks of momentum.';

CREATE INDEX idx_progression_xp    ON user_progression (total_xp DESC);
CREATE INDEX idx_progression_level ON user_progression (level DESC);

-- -----------------------------------------------------------------------------
-- xp_transactions — append-only XP ledger
-- -----------------------------------------------------------------------------
CREATE TABLE xp_transactions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    amount      INT         NOT NULL,
    reason      VARCHAR(40) NOT NULL,
    source_type VARCHAR(30),
    source_id   BIGINT,
    multiplier  NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_xp_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_xp_reason CHECK (reason IN
        ('skill_mastered', 'evidence_issued', 'viva_passed', 'project_verified',
         'assessment_completed', 'streak_milestone', 'badge_earned', 'quest_completed',
         'arena_placement', 'first_correct', 'comeback', 'admin_grant')),
    -- An XP event is idempotent per source: replaying an event cannot double-award
    CONSTRAINT uq_xp_source UNIQUE (user_id, reason, source_type, source_id)
);

COMMENT ON TABLE xp_transactions IS
'Append-only XP ledger; total_xp on user_progression is a cached sum of these rows and can always be rebuilt from them. The UNIQUE constraint makes awards idempotent, so a retried or replayed event never grants XP twice.';

CREATE INDEX idx_xp_user_time ON xp_transactions (user_id, created_at DESC);
CREATE INDEX idx_xp_reason    ON xp_transactions (reason);

-- -----------------------------------------------------------------------------
-- badges
-- -----------------------------------------------------------------------------
CREATE TABLE badges (
    id            BIGSERIAL PRIMARY KEY,
    slug          VARCHAR(80)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(400) NOT NULL,
    icon          VARCHAR(50),
    tier          VARCHAR(20)  NOT NULL DEFAULT 'bronze',
    category      VARCHAR(30)  NOT NULL DEFAULT 'mastery',
    -- Machine-checkable rule, e.g. {"type":"skills_mastered","threshold":10}
    criteria      JSONB        NOT NULL,
    xp_reward     INT          NOT NULL DEFAULT 0,
    is_secret     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_badges_slug UNIQUE (slug),
    CONSTRAINT ck_badges_tier CHECK (tier IN ('bronze', 'silver', 'gold', 'platinum')),
    CONSTRAINT ck_badges_cat  CHECK (category IN ('mastery', 'consistency', 'verification', 'social', 'milestone'))
);
COMMENT ON COLUMN badges.is_secret IS 'Hidden until earned — surprise awards are a stronger motivator than a visible checklist.';

CREATE TABLE user_badges (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    badge_id   BIGINT      NOT NULL,
    -- Progress toward a multi-step badge, 0..1
    progress   NUMERIC(4,3) NOT NULL DEFAULT 0.000,
    earned_at  TIMESTAMPTZ,
    seen_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_badge  UNIQUE (user_id, badge_id),
    CONSTRAINT fk_ub_user     FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_ub_badge    FOREIGN KEY (badge_id) REFERENCES badges (id) ON DELETE CASCADE,
    CONSTRAINT ck_ub_progress CHECK (progress >= 0 AND progress <= 1)
);
-- Drives the "new badge!" toast
CREATE INDEX idx_ub_unseen ON user_badges (user_id) WHERE earned_at IS NOT NULL AND seen_at IS NULL;
CREATE INDEX idx_ub_user   ON user_badges (user_id, earned_at DESC);

-- -----------------------------------------------------------------------------
-- quests — time-boxed goals
-- -----------------------------------------------------------------------------
CREATE TABLE quests (
    id            BIGSERIAL PRIMARY KEY,
    slug          VARCHAR(80)  NOT NULL,
    title         VARCHAR(150) NOT NULL,
    description   VARCHAR(400),
    cadence       VARCHAR(20)  NOT NULL DEFAULT 'weekly',
    -- {"type":"answer_correct","count":20} / {"type":"master_skill","count":1}
    objective     JSONB        NOT NULL,
    xp_reward     INT          NOT NULL DEFAULT 50,
    badge_id      BIGINT,
    starts_at     TIMESTAMPTZ,
    ends_at       TIMESTAMPTZ,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_quests_slug UNIQUE (slug),
    CONSTRAINT fk_quests_badge FOREIGN KEY (badge_id) REFERENCES badges (id) ON DELETE SET NULL,
    CONSTRAINT ck_quests_cadence CHECK (cadence IN ('daily', 'weekly', 'seasonal', 'one_off'))
);

CREATE TABLE user_quests (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    quest_id     BIGINT      NOT NULL,
    -- Period key ("2026-W37") so a weekly quest can recur without duplicate rows
    period_key   VARCHAR(20) NOT NULL,
    progress     INT         NOT NULL DEFAULT 0,
    target       INT         NOT NULL,
    completed_at TIMESTAMPTZ,
    claimed_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_quest_period UNIQUE (user_id, quest_id, period_key),
    CONSTRAINT fk_uq_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_uq_quest FOREIGN KEY (quest_id) REFERENCES quests (id) ON DELETE CASCADE,
    CONSTRAINT ck_uq_progress CHECK (progress >= 0)
);
CREATE INDEX idx_uq_user_open ON user_quests (user_id) WHERE completed_at IS NULL;

-- -----------------------------------------------------------------------------
-- Seasonal leaderboards
-- -----------------------------------------------------------------------------
CREATE TABLE leaderboard_seasons (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    scope      VARCHAR(20)  NOT NULL DEFAULT 'global',
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ  NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_season_slug UNIQUE (slug),
    CONSTRAINT ck_season_scope CHECK (scope IN ('global', 'course', 'cohort')),
    CONSTRAINT ck_season_dates CHECK (ends_at > starts_at)
);

COMMENT ON TABLE leaderboard_seasons IS
'Leaderboards reset per season so a learner who joins late is never permanently behind — a permanent all-time board demotivates everyone outside the top few.';

CREATE TABLE leaderboard_entries (
    id          BIGSERIAL PRIMARY KEY,
    season_id   BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    course_id   BIGINT,
    points      INT         NOT NULL DEFAULT 0,
    rank        INT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_leaderboard_entry UNIQUE (season_id, user_id, course_id),
    CONSTRAINT fk_le_season FOREIGN KEY (season_id) REFERENCES leaderboard_seasons (id) ON DELETE CASCADE,
    CONSTRAINT fk_le_user   FOREIGN KEY (user_id)   REFERENCES users (id)               ON DELETE CASCADE,
    CONSTRAINT fk_le_course FOREIGN KEY (course_id) REFERENCES courses (id)             ON DELETE CASCADE
);

COMMENT ON TABLE leaderboard_entries IS 'Durable record only. Live ranking during a session is served from Redis sorted sets; this table is the periodic flush and the post-season archive.';

CREATE INDEX idx_le_season_points ON leaderboard_entries (season_id, points DESC);
CREATE INDEX idx_le_user          ON leaderboard_entries (user_id);

-- -----------------------------------------------------------------------------
-- Seed: the level curve
-- -----------------------------------------------------------------------------
CREATE TABLE level_thresholds (
    level       INT     PRIMARY KEY,
    xp_required INT     NOT NULL,
    title       VARCHAR(60),

    CONSTRAINT ck_level_positive CHECK (level >= 1 AND xp_required >= 0)
);

COMMENT ON TABLE level_thresholds IS 'Explicit table rather than a formula, so the curve can be retuned without a code change or a data migration.';

INSERT INTO level_thresholds (level, xp_required, title) VALUES
    (1,     0, 'Beginner'),
    (2,   100, 'Learner'),
    (3,   300, 'Apprentice'),
    (4,   600, 'Practitioner'),
    (5,  1000, 'Skilled'),
    (6,  1600, 'Proficient'),
    (7,  2400, 'Advanced'),
    (8,  3500, 'Expert'),
    (9,  5000, 'Specialist'),
    (10, 7000, 'Master');
