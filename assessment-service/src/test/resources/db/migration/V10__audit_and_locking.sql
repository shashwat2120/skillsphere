-- =============================================================================
-- V10 — Audit columns and optimistic locking
--
-- Reconciles the schema with the JPA entity hierarchy. Written against the
-- actual columns created by V1–V9 rather than against assumption: an earlier
-- draft of this migration tried to add created_at to misconceptions, which V4
-- already creates, and Flyway rejected it. Worth stating plainly because it is
-- the argument for ddl-auto: validate — the mismatch surfaced at startup
-- instead of at the first insert.
--
-- Deliberately minimal. Tables carrying meaningful domain timestamps
-- (enrollments.enrolled_at, assessments.started_at, confusion_signals.detected_at,
-- learner_career_goals.set_at) are NOT given generic audit columns; those
-- entities map to BaseEntity and keep the timestamp that actually means
-- something. Adding created_at beside enrolled_at would be two columns holding
-- one fact, and they would drift.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Optimistic locking — only where writers genuinely contend
--
-- A version column costs a conflict on every contended update, so it is applied
-- to three tables rather than sixty-eight:
--
--   learner_skill_state  a learner answering live while the scheduled decay job
--                        recalculates the same row. A lost update here silently
--                        corrupts the mastery figure a skill claim rests on, and
--                        nothing would ever surface the error.
--   user_progression     XP, streaks and badge counts arrive concurrently from
--                        several independent event consumers.
--   arena_participants   scores update on every answer during a live session.
-- -----------------------------------------------------------------------------
ALTER TABLE learner_skill_state ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_progression    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE arena_participants  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN learner_skill_state.version IS
    'Optimistic lock. A conflict means a concurrent write was prevented, not that something failed — callers retry.';

-- -----------------------------------------------------------------------------
-- created_at where the table tracks only its last change
-- -----------------------------------------------------------------------------
ALTER TABLE learner_skill_state ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE user_progression    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE arena_participants  ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- -----------------------------------------------------------------------------
-- updated_at where the table records creation but not modification
-- -----------------------------------------------------------------------------
ALTER TABLE arena_participants      ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE user_devices            ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE user_badges             ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE user_quests             ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE viva_sessions           ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE instructor_applications ADD COLUMN updated_at TIMESTAMPTZ;

-- -----------------------------------------------------------------------------
-- Spring Modulith event publication registry — the transactional outbox.
--
-- A domain change and its outgoing event commit in the same local transaction,
-- so an event can never be lost after a successful write. Created here rather
-- than by Modulith's own initializer so Flyway remains the single owner of the
-- schema and Hibernate can keep running with ddl-auto: validate.
--
-- In Sprint 6 a relay publishes these rows to Kafka; no application code changes.
-- -----------------------------------------------------------------------------
-- Column list mirrors Modulith 2.1.1's JpaEventPublication exactly. It is
-- framework-owned, so it is transcribed from the entity rather than designed
-- here — a missing column fails Hibernate validation at startup, which is how
-- an earlier draft of this table was caught.
CREATE TABLE IF NOT EXISTS event_publication (
    id                     UUID         NOT NULL,
    listener_id            VARCHAR(255) NOT NULL,
    event_type             VARCHAR(255) NOT NULL,
    serialized_event       VARCHAR(255) NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    completion_attempts    INTEGER      NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    status                 VARCHAR(255),

    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

-- Incomplete publications are the ones the republishing job scans for.
CREATE INDEX IF NOT EXISTS idx_event_publication_incomplete
    ON event_publication (publication_date)
    WHERE completion_date IS NULL;

CREATE INDEX IF NOT EXISTS idx_event_publication_completion
    ON event_publication (completion_date);

COMMENT ON TABLE event_publication IS
    'Spring Modulith outbox. Rows with a null completion_date are events whose listener has not yet succeeded, and are retried on restart.';
