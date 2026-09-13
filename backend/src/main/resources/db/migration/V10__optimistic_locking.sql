-- =============================================================================
-- V10 — Optimistic locking
--
-- Adds a version column to the small set of tables that genuinely have
-- concurrent writers. Applied narrowly on purpose: a version column costs a
-- write conflict on every contended update, and most tables here have exactly
-- one writer, so adding it everywhere would buy nothing and cost throughput.
--
-- The three that need it:
--   learner_skill_state  — a learner answering live while the scheduled decay
--                          job recalculates the same row. A lost update here
--                          silently corrupts the mastery figure a skill claim
--                          rests on, and nothing would ever surface the error.
--   user_progression     — XP, streaks and badge counts arrive from several
--                          independent event consumers concurrently.
--   arena_participants   — scores update on every answer during a live session.
-- =============================================================================

ALTER TABLE learner_skill_state ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_progression    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE arena_participants  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN learner_skill_state.version IS
    'Optimistic lock. A conflict here means a concurrent write was prevented — callers should retry rather than treat it as a failure.';

-- -----------------------------------------------------------------------------
-- created_at / updated_at parity
--
-- JPA auditing writes both columns on entities that extend AuditableEntity, and
-- Hibernate runs with ddl-auto: validate, so any table mapped that way must
-- actually have both. These tables were defined with created_at only.
-- Append-only tables (responses, evidence, process_events, xp_transactions,
-- learning_events, skill_mastery_history) are deliberately excluded: they map
-- to BaseEntity and must never gain a modification timestamp, because a
-- mutable audit record is not an audit record.
-- -----------------------------------------------------------------------------

ALTER TABLE user_devices            ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE misconceptions          ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE assessments             ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE submissions             ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE viva_sessions           ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE learner_career_goals    ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE user_badges             ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE user_quests             ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE arena_participants      ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE confusion_signals       ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE enrollments             ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE instructor_applications ADD COLUMN updated_at TIMESTAMPTZ;
