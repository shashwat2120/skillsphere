-- =============================================================================
-- V16 — Uppercase enum literals on the arena/real-time tables
--
-- Same trap as V14 and V15: V8 predates V12's normalisation and was never
-- caught because the realtime module was still unbuilt. No rows exist yet,
-- so every UPDATE below is a no-op today.
-- =============================================================================

ALTER TABLE arenas DROP CONSTRAINT ck_arena_status;
ALTER TABLE arenas ALTER COLUMN status DROP DEFAULT;
UPDATE arenas SET status = upper(status);
ALTER TABLE arenas ALTER COLUMN status SET DEFAULT 'LOBBY';
ALTER TABLE arenas ADD CONSTRAINT ck_arena_status CHECK (status IN ('LOBBY', 'RUNNING', 'PAUSED', 'ENDED'));

ALTER TABLE confusion_signals DROP CONSTRAINT ck_cs_severity;
ALTER TABLE confusion_signals ALTER COLUMN severity DROP DEFAULT;
UPDATE confusion_signals SET severity = upper(severity);
ALTER TABLE confusion_signals ALTER COLUMN severity SET DEFAULT 'MEDIUM';
ALTER TABLE confusion_signals ADD CONSTRAINT ck_cs_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH'));
