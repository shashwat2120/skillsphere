-- =============================================================================
-- V2 — Local read-model mirror of assessment-service's skill catalog
--
-- This service's only cross-service skill read is SkillLookup#namesOf (see
-- VerificationController), which used to be a JdbcTemplate query against
-- the shared skills table. Now that assessment-service owns that table in
-- its own database, this mirror replaces the read — kept current by
-- SkillCatalogEventListener consuming assessment-service's
-- skill-catalog-events topic, not queried live on every lookup. See
-- assessment-service's SkillCatalogChanged for the full reasoning: skills
-- change on the order of once a semester, so a permanent runtime
-- dependency on assessment-service for a name lookup would be a worse
-- trade than a Kafka-synced local copy.
--
-- Seeded with the same static catalog every service's skill data
-- ultimately traces back to (the original monolith's V899__skill_graph.sql),
-- so a fresh clone starts consistent without needing a snapshot pull —
-- only future deltas need to travel over Kafka at all.
-- =============================================================================

CREATE TABLE skills_mirror (
    id         BIGINT       PRIMARY KEY,
    slug       VARCHAR(120) NOT NULL,
    name       VARCHAR(150) NOT NULL,
    level_band VARCHAR(20)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE skills_mirror IS
'Read-only local copy of assessment-service''s skills table, kept current via Kafka. Never written to by anything in this service except SkillCatalogEventListener.';

INSERT INTO skills_mirror (id, slug, name, level_band, active) VALUES
    (1, 'java-syntax',    'Java Syntax',                  'FOUNDATIONAL', TRUE),
    (2, 'oop',            'Object-Oriented Programming',  'FOUNDATIONAL', TRUE),
    (3, 'collections',    'Collections',                  'INTERMEDIATE', TRUE),
    (4, 'generics',       'Generics',                      'INTERMEDIATE', TRUE),
    (5, 'streams',        'Streams API',                   'INTERMEDIATE', TRUE),
    (6, 'concurrency',    'Concurrency',                   'ADVANCED',     TRUE),
    (7, 'spring-boot',    'Spring Boot',                   'ADVANCED',     TRUE),
    (8, 'microservices',  'Microservices',                 'EXPERT',       TRUE)
ON CONFLICT (id) DO NOTHING;
