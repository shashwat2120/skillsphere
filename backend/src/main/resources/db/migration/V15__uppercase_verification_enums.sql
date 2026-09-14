-- =============================================================================
-- V15 — Uppercase enum literals on the verification tables
--
-- V5 predates V12, the same way V6's career tables did (fixed in V14). No
-- rows exist yet in any of these tables — the verification module is unbuilt
-- — so every UPDATE below is a no-op today and exists only so this migration
-- stays safe to run against a database that has since picked up demo data.
-- =============================================================================

ALTER TABLE projects DROP CONSTRAINT ck_projects_status;
ALTER TABLE projects DROP CONSTRAINT ck_projects_band;
ALTER TABLE projects ALTER COLUMN status DROP DEFAULT;
ALTER TABLE projects ALTER COLUMN level_band DROP DEFAULT;
UPDATE projects SET status = upper(status), level_band = upper(level_band);
ALTER TABLE projects ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE projects ALTER COLUMN level_band SET DEFAULT 'INTERMEDIATE';
ALTER TABLE projects ADD CONSTRAINT ck_projects_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));
ALTER TABLE projects ADD CONSTRAINT ck_projects_band
    CHECK (level_band IN ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'));

ALTER TABLE submissions DROP CONSTRAINT ck_sub_status;
ALTER TABLE submissions ALTER COLUMN status DROP DEFAULT;
UPDATE submissions SET status = upper(status);
ALTER TABLE submissions ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE submissions ADD CONSTRAINT ck_sub_status CHECK (status IN
    ('DRAFT', 'SUBMITTED', 'IN_VIVA', 'AWAITING_REVIEW', 'VERIFIED', 'NOT_VERIFIED'));

ALTER TABLE process_events DROP CONSTRAINT ck_pe_type;
UPDATE process_events SET event_type = upper(event_type);
ALTER TABLE process_events ADD CONSTRAINT ck_pe_type CHECK (event_type IN
    ('SESSION_START', 'SESSION_END', 'DRAFT_SAVED', 'EDIT', 'PASTE',
     'LARGE_PASTE', 'RUN', 'TEST_PASS', 'TEST_FAIL', 'IDLE', 'AI_CONSULTED'));

ALTER TABLE ai_usage_declarations DROP CONSTRAINT ck_aiud_purpose;
ALTER TABLE ai_usage_declarations DROP CONSTRAINT ck_aiud_extent;
UPDATE ai_usage_declarations SET purpose = upper(purpose), extent = upper(extent);
ALTER TABLE ai_usage_declarations ADD CONSTRAINT ck_aiud_purpose CHECK (purpose IN
    ('BRAINSTORMING', 'EXPLANATION', 'DEBUGGING', 'CODE_GENERATION', 'REVIEW', 'WRITING', 'NONE'));
ALTER TABLE ai_usage_declarations ADD CONSTRAINT ck_aiud_extent
    CHECK (extent IN ('NONE', 'MINOR', 'MODERATE', 'SUBSTANTIAL'));

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
ALTER TABLE viva_turns ADD CONSTRAINT ck_vt_intent CHECK (question_intent IS NULL OR question_intent IN
    ('DESIGN_CHOICE', 'TRADE_OFF', 'EDGE_CASE', 'ALTERNATIVE', 'FAILURE_MODE', 'CONCEPT_CHECK'));

ALTER TABLE instructor_reviews DROP CONSTRAINT ck_ir_decision;
UPDATE instructor_reviews SET decision = upper(decision);
ALTER TABLE instructor_reviews ADD CONSTRAINT ck_ir_decision
    CHECK (decision IN ('APPROVED', 'REJECTED', 'REVISIONS_REQUESTED'));

ALTER TABLE evidence DROP CONSTRAINT ck_ev_type;
ALTER TABLE evidence DROP CONSTRAINT ck_ev_source;
UPDATE evidence SET evidence_type = upper(evidence_type), source_type = upper(source_type);
ALTER TABLE evidence ADD CONSTRAINT ck_ev_type
    CHECK (evidence_type IN ('ASSESSMENT', 'PROJECT', 'VIVA', 'INSTRUCTOR', 'ARENA'));
ALTER TABLE evidence ADD CONSTRAINT ck_ev_source CHECK (source_type IN
    ('ASSESSMENT', 'SUBMISSION', 'VIVA_SESSION', 'INSTRUCTOR_REVIEW', 'ARENA'));
