-- =============================================================================
-- V17 — Analytics: a queryable correctness column, and the usual uppercase fix
--
-- learning_events.correct: whether a response was right is a fact accuracy
-- rollups and risk scoring need to query constantly (COUNT ... WHERE correct),
-- not an occasional detail worth burying inside the JSONB payload where it
-- would need to be parsed out of every row on every read. Nullable, because
-- not every event type has a pass/fail notion — a future "lesson_completed"
-- event, say — so this is additive for event types that do, not a constraint
-- on the ones that don't.
--
-- risk_scores.band predates V12 the same way V8 and V5 did (fixed in V16 and
-- V15) — added by V9, before the uppercase convention was established.
-- =============================================================================

ALTER TABLE learning_events ADD COLUMN correct BOOLEAN;
CREATE INDEX idx_le_correct ON learning_events (occurred_at) WHERE correct IS NOT NULL;

ALTER TABLE risk_scores DROP CONSTRAINT ck_rs_band;
UPDATE risk_scores SET band = upper(band);
ALTER TABLE risk_scores ADD CONSTRAINT ck_rs_band CHECK (band IN ('LOW', 'MEDIUM', 'HIGH'));
