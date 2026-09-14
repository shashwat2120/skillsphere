-- =============================================================================
-- V14 — Uppercase enum literals on career_roles, learning_paths, path_steps
--
-- V12 normalised every CHECK constraint that stored a JPA @Enumerated(STRING)
-- value to match what Hibernate actually writes (Java enum names are
-- uppercase). career_roles, learning_paths and path_steps were added by V6,
-- after V12 ran, and were written with the pre-V12 lowercase convention —
-- the same trap, just discovered before ddl-auto: validate had to catch it
-- at startup instead of after. No rows exist in these tables yet (the career
-- module is unbuilt), so the UPDATE statements are no-ops today and are here
-- only so this migration is safe to run again on a database that already has
-- demo data by the time this ships.
-- =============================================================================

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
    CHECK (generation_reason IN
        ('INITIAL', 'DIAGNOSTIC_COMPLETED', 'MASTERY_CHANGED', 'GOAL_CHANGED', 'DECAY_DETECTED', 'MANUAL'));

ALTER TABLE path_steps DROP CONSTRAINT ck_ps_activity;
ALTER TABLE path_steps DROP CONSTRAINT ck_ps_status;
ALTER TABLE path_steps ALTER COLUMN status DROP DEFAULT;
UPDATE path_steps SET activity_type = upper(activity_type), status = upper(status);
ALTER TABLE path_steps ALTER COLUMN status SET DEFAULT 'LOCKED';
ALTER TABLE path_steps ADD CONSTRAINT ck_ps_activity
    CHECK (activity_type IN ('LESSON', 'PRACTICE', 'ASSESSMENT', 'PROJECT', 'REVIEW'));
ALTER TABLE path_steps ADD CONSTRAINT ck_ps_status
    CHECK (status IN ('LOCKED', 'AVAILABLE', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED'));
