-- =============================================================================
-- V12 — Normalise every enum literal to uppercase
--
-- JPA's @Enumerated(STRING) persists an enum by its Java constant name, which is
-- uppercase by convention. V2–V9 were written with lowercase literals, so the
-- first insert against any of these tables would have been rejected by its own
-- CHECK constraint — the same defect already found and fixed in V1 for
-- users.status and roles.name.
--
-- WHY A FORWARD MIGRATION RATHER THAN EDITING V2–V9.
-- V1 was editable because no database existed yet. These have been applied, and
-- Flyway records a checksum per migration: altering an applied file makes the
-- application refuse to start with a validation error, and forcing past that
-- means every other environment silently diverges. Editing applied migrations
-- is the habit that breaks the first time something is actually deployed, so it
-- is not a habit worth having even while nothing is.
--
-- The UPDATE statements are no-ops on an empty database. They are written anyway
-- because this migration must also be correct for an environment that already
-- holds data — which is the entire point of choosing a forward migration.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Helper: each block drops the constraint, lifts existing data and the column
-- default to uppercase, then restores the constraint over the new vocabulary.
-- Order matters — the constraint must be absent while the data is rewritten.
-- -----------------------------------------------------------------------------

-- ---- skills -----------------------------------------------------------------
ALTER TABLE skills DROP CONSTRAINT ck_skills_band;
ALTER TABLE skills ALTER COLUMN level_band DROP DEFAULT;
UPDATE skills SET level_band = upper(level_band);
ALTER TABLE skills ALTER COLUMN level_band SET DEFAULT 'INTERMEDIATE';
ALTER TABLE skills ADD CONSTRAINT ck_skills_band
    CHECK (level_band IN ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'));

-- ---- skill_mastery_history --------------------------------------------------
ALTER TABLE skill_mastery_history DROP CONSTRAINT ck_smh_trigger;
UPDATE skill_mastery_history SET trigger_type = upper(trigger_type);
ALTER TABLE skill_mastery_history ADD CONSTRAINT ck_smh_trigger
    CHECK (trigger_type IN ('RESPONSE', 'ASSESSMENT', 'PROJECT', 'VIVA', 'INSTRUCTOR', 'DECAY', 'INITIAL'));

-- ---- courses ----------------------------------------------------------------
ALTER TABLE courses DROP CONSTRAINT ck_courses_status;
ALTER TABLE courses DROP CONSTRAINT ck_courses_band;
ALTER TABLE courses ALTER COLUMN status DROP DEFAULT;
ALTER TABLE courses ALTER COLUMN level_band DROP DEFAULT;
UPDATE courses SET status = upper(status), level_band = upper(level_band);
ALTER TABLE courses ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE courses ALTER COLUMN level_band SET DEFAULT 'INTERMEDIATE';
ALTER TABLE courses ADD CONSTRAINT ck_courses_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));
ALTER TABLE courses ADD CONSTRAINT ck_courses_band
    CHECK (level_band IN ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'));

-- ---- lessons ----------------------------------------------------------------
ALTER TABLE lessons DROP CONSTRAINT ck_lessons_type;
ALTER TABLE lessons ALTER COLUMN type DROP DEFAULT;
UPDATE lessons SET type = upper(type);
ALTER TABLE lessons ALTER COLUMN type SET DEFAULT 'TEXT';
ALTER TABLE lessons ADD CONSTRAINT ck_lessons_type
    CHECK (type IN ('TEXT', 'VIDEO', 'RESOURCE', 'INTERACTIVE'));

-- ---- enrollments ------------------------------------------------------------
ALTER TABLE enrollments DROP CONSTRAINT ck_enroll_status;
ALTER TABLE enrollments DROP CONSTRAINT ck_enroll_source;
ALTER TABLE enrollments ALTER COLUMN status DROP DEFAULT;
ALTER TABLE enrollments ALTER COLUMN source DROP DEFAULT;
UPDATE enrollments SET status = upper(status), source = upper(source);
ALTER TABLE enrollments ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE enrollments ALTER COLUMN source SET DEFAULT 'SELF';
ALTER TABLE enrollments ADD CONSTRAINT ck_enroll_status
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'DROPPED'));
ALTER TABLE enrollments ADD CONSTRAINT ck_enroll_source
    CHECK (source IN ('SELF', 'PATH', 'ASSIGNED'));

-- ---- lesson_progress --------------------------------------------------------
ALTER TABLE lesson_progress DROP CONSTRAINT ck_lprog_status;
ALTER TABLE lesson_progress ALTER COLUMN status DROP DEFAULT;
UPDATE lesson_progress SET status = upper(status);
ALTER TABLE lesson_progress ALTER COLUMN status SET DEFAULT 'STARTED';
ALTER TABLE lesson_progress ADD CONSTRAINT ck_lprog_status
    CHECK (status IN ('STARTED', 'COMPLETED'));

-- ---- items ------------------------------------------------------------------
ALTER TABLE items DROP CONSTRAINT ck_items_type;
ALTER TABLE items DROP CONSTRAINT ck_items_status;
ALTER TABLE items ALTER COLUMN type DROP DEFAULT;
ALTER TABLE items ALTER COLUMN status DROP DEFAULT;
UPDATE items SET type = upper(type), status = upper(status);
ALTER TABLE items ALTER COLUMN type SET DEFAULT 'MCQ';
ALTER TABLE items ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE items ADD CONSTRAINT ck_items_type
    CHECK (type IN ('MCQ', 'MULTI_SELECT', 'NUMERIC', 'SHORT_TEXT', 'CODE'));
ALTER TABLE items ADD CONSTRAINT ck_items_status
    CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'));

-- ---- assessments ------------------------------------------------------------
ALTER TABLE assessments DROP CONSTRAINT ck_assess_type;
ALTER TABLE assessments DROP CONSTRAINT ck_assess_status;
ALTER TABLE assessments DROP CONSTRAINT ck_assess_term;
ALTER TABLE assessments ALTER COLUMN status DROP DEFAULT;
UPDATE assessments SET type = upper(type), status = upper(status),
                       termination_reason = upper(termination_reason);
ALTER TABLE assessments ALTER COLUMN status SET DEFAULT 'IN_PROGRESS';
ALTER TABLE assessments ADD CONSTRAINT ck_assess_type
    CHECK (type IN ('DIAGNOSTIC', 'PRACTICE', 'CHECKPOINT', 'ARENA'));
ALTER TABLE assessments ADD CONSTRAINT ck_assess_status
    CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED'));
ALTER TABLE assessments ADD CONSTRAINT ck_assess_term
    CHECK (termination_reason IS NULL OR termination_reason IN
        ('CONFIDENCE_REACHED', 'ITEM_CAP', 'TIME_LIMIT', 'ABANDONED', 'NO_ITEMS_AVAILABLE'));

-- ---- projects ---------------------------------------------------------------
ALTER TABLE projects DROP CONSTRAINT ck_projects_status;
ALTER TABLE projects DROP CONSTRAINT ck_projects_band;
ALTER TABLE projects ALTER COLUMN status DROP DEFAULT;
ALTER TABLE projects ALTER COLUMN level_band DROP DEFAULT;
UPDATE projects SET status = upper(status), level_band = upper(level_band);
ALTER TABLE projects ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE projects ALTER COLUMN level_band SET DEFAULT 'INTERMEDIATE';
ALTER TABLE projects ADD CONSTRAINT ck_projects_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));
ALTER TABLE projects ADD CONSTRAINT ck_projects_band
    CHECK (level_band IN ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'));

-- ---- submissions ------------------------------------------------------------
ALTER TABLE submissions DROP CONSTRAINT ck_sub_status;
ALTER TABLE submissions ALTER COLUMN status DROP DEFAULT;
UPDATE submissions SET status = upper(status);
ALTER TABLE submissions ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE submissions ADD CONSTRAINT ck_sub_status
    CHECK (status IN ('DRAFT', 'SUBMITTED', 'IN_VIVA', 'AWAITING_REVIEW', 'VERIFIED', 'NOT_VERIFIED'));

-- ---- process_events ---------------------------------------------------------
ALTER TABLE process_events DROP CONSTRAINT ck_pe_type;
UPDATE process_events SET event_type = upper(event_type);
ALTER TABLE process_events ADD CONSTRAINT ck_pe_type
    CHECK (event_type IN ('SESSION_START', 'SESSION_END', 'DRAFT_SAVED', 'EDIT', 'PASTE',
                          'LARGE_PASTE', 'RUN', 'TEST_PASS', 'TEST_FAIL', 'IDLE', 'AI_CONSULTED'));

-- ---- ai_usage_declarations --------------------------------------------------
ALTER TABLE ai_usage_declarations DROP CONSTRAINT ck_aiud_purpose;
ALTER TABLE ai_usage_declarations DROP CONSTRAINT ck_aiud_extent;
UPDATE ai_usage_declarations SET purpose = upper(purpose), extent = upper(extent);
ALTER TABLE ai_usage_declarations ADD CONSTRAINT ck_aiud_purpose
    CHECK (purpose IN ('BRAINSTORMING', 'EXPLANATION', 'DEBUGGING', 'CODE_GENERATION',
                       'REVIEW', 'WRITING', 'NONE'));
ALTER TABLE ai_usage_declarations ADD CONSTRAINT ck_aiud_extent
    CHECK (extent IN ('NONE', 'MINOR', 'MODERATE', 'SUBSTANTIAL'));

-- ---- viva_sessions / viva_turns ---------------------------------------------
ALTER TABLE viva_sessions DROP CONSTRAINT ck_viva_status;
ALTER TABLE viva_sessions DROP CONSTRAINT ck_viva_verdict;
ALTER TABLE viva_sessions ALTER COLUMN status DROP DEFAULT;
UPDATE viva_sessions SET status = upper(status), verdict = upper(verdict);
ALTER TABLE viva_sessions ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE viva_sessions ADD CONSTRAINT ck_viva_status
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED'));
ALTER TABLE viva_sessions ADD CONSTRAINT ck_viva_verdict
    CHECK (verdict IS NULL OR verdict IN ('VERIFIED', 'NOT_VERIFIED', 'INCONCLUSIVE'));

ALTER TABLE viva_turns DROP CONSTRAINT ck_vt_intent;
UPDATE viva_turns SET question_intent = upper(question_intent);
ALTER TABLE viva_turns ADD CONSTRAINT ck_vt_intent
    CHECK (question_intent IS NULL OR question_intent IN
        ('DESIGN_CHOICE', 'TRADE_OFF', 'EDGE_CASE', 'ALTERNATIVE', 'FAILURE_MODE', 'CONCEPT_CHECK'));

-- ---- instructor_reviews / evidence ------------------------------------------
ALTER TABLE instructor_reviews DROP CONSTRAINT ck_ir_decision;
UPDATE instructor_reviews SET decision = upper(decision);
ALTER TABLE instructor_reviews ADD CONSTRAINT ck_ir_decision
    CHECK (decision IN ('APPROVED', 'REJECTED', 'REVISIONS_REQUESTED'));

ALTER TABLE evidence DROP CONSTRAINT ck_ev_type;
ALTER TABLE evidence DROP CONSTRAINT ck_ev_source;
UPDATE evidence SET evidence_type = upper(evidence_type), source_type = upper(source_type);
ALTER TABLE evidence ADD CONSTRAINT ck_ev_type
    CHECK (evidence_type IN ('ASSESSMENT', 'PROJECT', 'VIVA', 'INSTRUCTOR', 'ARENA'));
ALTER TABLE evidence ADD CONSTRAINT ck_ev_source
    CHECK (source_type IN ('ASSESSMENT', 'SUBMISSION', 'VIVA_SESSION', 'INSTRUCTOR_REVIEW', 'ARENA'));

-- ---- career_roles / paths ---------------------------------------------------
ALTER TABLE career_roles DROP CONSTRAINT ck_career_seniority;
ALTER TABLE career_roles ALTER COLUMN seniority DROP DEFAULT;
UPDATE career_roles SET seniority = upper(seniority);
ALTER TABLE career_roles ALTER COLUMN seniority SET DEFAULT 'JUNIOR';
ALTER TABLE career_roles ADD CONSTRAINT ck_career_seniority
    CHECK (seniority IN ('INTERN', 'JUNIOR', 'MID', 'SENIOR'));

ALTER TABLE learning_paths DROP CONSTRAINT ck_lp_status;
ALTER TABLE learning_paths DROP CONSTRAINT ck_lp_reason;
ALTER TABLE learning_paths ALTER COLUMN status DROP DEFAULT;
ALTER TABLE learning_paths ALTER COLUMN generation_reason DROP DEFAULT;
UPDATE learning_paths SET status = upper(status), generation_reason = upper(generation_reason);
ALTER TABLE learning_paths ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE learning_paths ALTER COLUMN generation_reason SET DEFAULT 'INITIAL';
ALTER TABLE learning_paths ADD CONSTRAINT ck_lp_status
    CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'COMPLETED', 'ABANDONED'));
ALTER TABLE learning_paths ADD CONSTRAINT ck_lp_reason
    CHECK (generation_reason IN ('INITIAL', 'DIAGNOSTIC_COMPLETED', 'MASTERY_CHANGED',
                                 'GOAL_CHANGED', 'DECAY_DETECTED', 'MANUAL'));

ALTER TABLE path_steps DROP CONSTRAINT ck_ps_activity;
ALTER TABLE path_steps DROP CONSTRAINT ck_ps_status;
ALTER TABLE path_steps ALTER COLUMN status DROP DEFAULT;
UPDATE path_steps SET activity_type = upper(activity_type), status = upper(status);
ALTER TABLE path_steps ALTER COLUMN status SET DEFAULT 'LOCKED';
ALTER TABLE path_steps ADD CONSTRAINT ck_ps_activity
    CHECK (activity_type IN ('LESSON', 'PRACTICE', 'ASSESSMENT', 'PROJECT', 'REVIEW'));
ALTER TABLE path_steps ADD CONSTRAINT ck_ps_status
    CHECK (status IN ('LOCKED', 'AVAILABLE', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'));

-- ---- gamification -----------------------------------------------------------
ALTER TABLE xp_transactions DROP CONSTRAINT ck_xp_reason;
UPDATE xp_transactions SET reason = upper(reason);
ALTER TABLE xp_transactions ADD CONSTRAINT ck_xp_reason
    CHECK (reason IN ('SKILL_MASTERED', 'EVIDENCE_ISSUED', 'VIVA_PASSED', 'PROJECT_VERIFIED',
                      'ASSESSMENT_COMPLETED', 'STREAK_MILESTONE', 'BADGE_EARNED', 'QUEST_COMPLETED',
                      'ARENA_PLACEMENT', 'FIRST_CORRECT', 'COMEBACK', 'ADMIN_GRANT'));

ALTER TABLE badges DROP CONSTRAINT ck_badges_tier;
ALTER TABLE badges DROP CONSTRAINT ck_badges_cat;
ALTER TABLE badges ALTER COLUMN tier DROP DEFAULT;
ALTER TABLE badges ALTER COLUMN category DROP DEFAULT;
UPDATE badges SET tier = upper(tier), category = upper(category);
ALTER TABLE badges ALTER COLUMN tier SET DEFAULT 'BRONZE';
ALTER TABLE badges ALTER COLUMN category SET DEFAULT 'MASTERY';
ALTER TABLE badges ADD CONSTRAINT ck_badges_tier
    CHECK (tier IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM'));
ALTER TABLE badges ADD CONSTRAINT ck_badges_cat
    CHECK (category IN ('MASTERY', 'CONSISTENCY', 'VERIFICATION', 'SOCIAL', 'MILESTONE'));

ALTER TABLE quests DROP CONSTRAINT ck_quests_cadence;
ALTER TABLE quests ALTER COLUMN cadence DROP DEFAULT;
UPDATE quests SET cadence = upper(cadence);
ALTER TABLE quests ALTER COLUMN cadence SET DEFAULT 'WEEKLY';
ALTER TABLE quests ADD CONSTRAINT ck_quests_cadence
    CHECK (cadence IN ('DAILY', 'WEEKLY', 'SEASONAL', 'ONE_OFF'));

ALTER TABLE leaderboard_seasons DROP CONSTRAINT ck_season_scope;
ALTER TABLE leaderboard_seasons ALTER COLUMN scope DROP DEFAULT;
UPDATE leaderboard_seasons SET scope = upper(scope);
ALTER TABLE leaderboard_seasons ALTER COLUMN scope SET DEFAULT 'GLOBAL';
ALTER TABLE leaderboard_seasons ADD CONSTRAINT ck_season_scope
    CHECK (scope IN ('GLOBAL', 'COURSE', 'COHORT'));

-- ---- arena ------------------------------------------------------------------
ALTER TABLE arenas DROP CONSTRAINT ck_arena_status;
ALTER TABLE arenas ALTER COLUMN status DROP DEFAULT;
UPDATE arenas SET status = upper(status);
ALTER TABLE arenas ALTER COLUMN status SET DEFAULT 'LOBBY';
ALTER TABLE arenas ADD CONSTRAINT ck_arena_status
    CHECK (status IN ('LOBBY', 'RUNNING', 'PAUSED', 'ENDED'));

ALTER TABLE confusion_signals DROP CONSTRAINT ck_cs_severity;
ALTER TABLE confusion_signals ALTER COLUMN severity DROP DEFAULT;
UPDATE confusion_signals SET severity = upper(severity);
ALTER TABLE confusion_signals ALTER COLUMN severity SET DEFAULT 'MEDIUM';
ALTER TABLE confusion_signals ADD CONSTRAINT ck_cs_severity
    CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH'));

-- ---- analytics / admin / notifications --------------------------------------
ALTER TABLE risk_scores DROP CONSTRAINT ck_rs_band;
UPDATE risk_scores SET band = upper(band);
ALTER TABLE risk_scores ADD CONSTRAINT ck_rs_band
    CHECK (band IN ('LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE instructor_applications DROP CONSTRAINT ck_ia_status;
ALTER TABLE instructor_applications ALTER COLUMN status DROP DEFAULT;
UPDATE instructor_applications SET status = upper(status);
ALTER TABLE instructor_applications ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE instructor_applications ADD CONSTRAINT ck_ia_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE notifications DROP CONSTRAINT ck_notif_prio;
ALTER TABLE notifications ALTER COLUMN priority DROP DEFAULT;
UPDATE notifications SET priority = upper(priority);
ALTER TABLE notifications ALTER COLUMN priority SET DEFAULT 'NORMAL';
ALTER TABLE notifications ADD CONSTRAINT ck_notif_prio
    CHECK (priority IN ('LOW', 'NORMAL', 'HIGH'));

ALTER TABLE notification_preferences DROP CONSTRAINT ck_np_channel;
UPDATE notification_preferences SET channel = upper(channel);
ALTER TABLE notification_preferences ADD CONSTRAINT ck_np_channel
    CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH'));

-- refresh_tokens.revoked_reason has no CHECK constraint, but the application
-- writes it lowercase. Normalised here so the column agrees with every other
-- enum-shaped column in the schema.
UPDATE refresh_tokens SET revoked_reason = upper(revoked_reason);
COMMENT ON COLUMN refresh_tokens.revoked_reason IS
    'LOGOUT | ROTATED | REUSE_DETECTED | PASSWORD_CHANGED | ADMIN_REVOKED';
