-- =============================================================================
-- V3 — Local read-model mirror of assessment-service's prerequisite edges
--
-- The one method every other dependent service's SkillLookupShim throws
-- UnsupportedOperationException on: hardPrerequisitesOf, called only by
-- PathGenerator (to topologically order a generated learning path) — see
-- SkillPrerequisiteChanged's own class comment for why career-service is
-- the sole mirror of this edge set. Small enough (10 rows today) to seed
-- as literal INSERT statements, same as skills_mirror; kept current going
-- forward by SkillPrerequisiteEventListener consuming
-- skill-prerequisite-events.
-- =============================================================================

CREATE TABLE skill_prerequisites_mirror (
    skill_id              BIGINT       NOT NULL,
    prerequisite_skill_id BIGINT       NOT NULL,
    strength              NUMERIC(3,2) NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_spm PRIMARY KEY (skill_id, prerequisite_skill_id)
);

COMMENT ON TABLE skill_prerequisites_mirror IS
'Read-only local copy of assessment-service''s skill_prerequisites table, kept current via Kafka. Never written to by anything in this service except SkillPrerequisiteEventListener.';

INSERT INTO skill_prerequisites_mirror (skill_id, prerequisite_skill_id, strength) VALUES
    (2, 1, 1.00),
    (3, 2, 1.00),
    (4, 2, 1.00),
    (5, 3, 1.00),
    (5, 4, 0.50),
    (6, 3, 1.00),
    (7, 2, 1.00),
    (7, 3, 0.50),
    (8, 6, 0.50),
    (8, 7, 1.00)
ON CONFLICT (skill_id, prerequisite_skill_id) DO NOTHING;
