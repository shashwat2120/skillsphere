-- =============================================================================
-- V13 — At most one in-progress assessment per learner
--
-- DiagnosticService.startOrResume() already assumes this: it abandons any
-- other in-progress assessment before starting a new one, and it looks the
-- current one up with a query that returns a single Optional. That second
-- part was never actually guaranteed by the schema — two concurrent
-- "start diagnostic" requests (a double-submit, two tabs, or React calling an
-- effect twice) could both see "nothing in progress" and both insert a row,
-- since nothing stopped them. The result is two IN_PROGRESS rows for the same
-- learner, which is not just untidy: findByUserIdAndStatus stops being able
-- to return a single result and every future resume throws instead of
-- serving the next question. Caught in development exactly this way.
--
-- The fix belongs at the database, not only in application code. Application
-- logic can reduce how often two requests race each other, but only a
-- constraint makes the invalid state impossible to reach at all.
-- =============================================================================

-- Existing violations first: keep the most recently started IN_PROGRESS
-- assessment per learner and abandon the rest. Abandoning rather than
-- deleting preserves the responses already recorded as evidence — the same
-- rule the application applies when a learner switches skills mid-diagnostic.
WITH ranked AS (
    SELECT id,
           row_number() OVER (PARTITION BY user_id ORDER BY started_at DESC, id DESC) AS rn
      FROM assessments
     WHERE status = 'IN_PROGRESS'
)
UPDATE assessments
   SET status = 'ABANDONED',
       termination_reason = 'ABANDONED',
       completed_at = now()
 WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- A partial unique index rather than a full one: it only ever needs to
-- constrain the IN_PROGRESS rows, and a learner can accumulate any number of
-- completed or abandoned assessments over time.
CREATE UNIQUE INDEX ux_assessments_one_in_progress_per_user
    ON assessments (user_id)
    WHERE status = 'IN_PROGRESS';
