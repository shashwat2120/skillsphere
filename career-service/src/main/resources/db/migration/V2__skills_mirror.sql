-- =============================================================================
-- V2 — Local read-model mirror of assessment-service's skill catalog
--
-- Same table, same reasoning as every other dependent service's own V2 —
-- SkillLookupShim's namesOf/findAllActive used to query the shared skills
-- table directly, and now reads this local copy instead, kept current by
-- SkillCatalogEventListener.
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
