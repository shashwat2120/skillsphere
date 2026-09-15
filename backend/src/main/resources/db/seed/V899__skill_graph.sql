-- =============================================================================
-- V899 — The skill graph itself: categories, skills, prerequisite edges.
--
-- WHY THIS FILE EXISTS, AND WHY IT WAS MISSING UNTIL NOW.
-- Every seed migration from V900 onward (item bank, courses, learner
-- history) assumes eight skills already exist and resolves them by slug —
-- correctly, and safely: a JOIN against an empty skills table just inserts
-- zero rows rather than erroring. That silence is exactly what hid this gap.
-- The eight skills in this codebase's dev database were never migrated at
-- all — they were created by hand through the running Admin UI while
-- Sprint 5's skill graph feature was being built and verified live. That
-- makes them look real in this developer's own machine and genuinely
-- absent in any fresh clone: `docker compose up` plus Flyway alone produces
-- zero rows in `skills`, and every seed migration after this one that joins
-- against it — the entire item bank, every course, every learner's history —
-- silently seeds nothing. V908's learner-persona inserts finally surfaced
-- this as a loud failure (a literal skill_id in a VALUES list, not a slug
-- JOIN, correctly throws an FK violation on an empty table instead of
-- staying quiet about it) — that failure is what this migration actually
-- fixes, not V908 itself.
--
-- WHAT'S BELOW is the exact content of this project's own working skill
-- graph, copied from the current development database (the closest thing
-- this project has to a source of truth for it, since no migration ever
-- captured it) rather than re-invented: one category (Java), eight skills
-- spanning FOUNDATIONAL through EXPERT, and the ten prerequisite edges that
-- give the frontier query ("what am I ready for?") something real to
-- traverse.
--
-- V899, not V900: Flyway orders every migration on its locations list by
-- version regardless of directory, and every later seed file depends on
-- these rows existing first.
-- =============================================================================

INSERT INTO skill_categories (id, name, slug, description, colour, position, created_at) VALUES
  (1, 'Java', 'java', NULL, NULL, 1, '2026-09-14 06:10:53.321462+00')
ON CONFLICT (id) DO NOTHING;

-- Keeps the sequence past the explicit id above, the same reason every
-- explicit-id insert in this codebase's migrations does this — the next
-- category created through the app must not collide with id 1.
SELECT setval('skill_categories_id_seq', GREATEST((SELECT id FROM skill_categories ORDER BY id DESC LIMIT 1), 1));

INSERT INTO skills (id, slug, name, description, category_id, level_band, est_minutes, decay_rate, is_active, created_at) VALUES
  (1, 'java-syntax',   'Java Syntax',                 'Variables, control flow, methods.',            1, 'FOUNDATIONAL', 45,  0.00100, true, now()),
  (2, 'oop',           'Object-Oriented Programming',  'Classes, inheritance, polymorphism.',          1, 'FOUNDATIONAL', 90,  0.00200, true, now()),
  (3, 'collections',   'Collections',                  'List, Map, Set and when to use each.',         1, 'INTERMEDIATE', 75,  0.00300, true, now()),
  (4, 'generics',      'Generics',                     'Type parameters and bounded types.',           1, 'INTERMEDIATE', 60,  0.00400, true, now()),
  (5, 'streams',       'Streams API',                  'Functional pipelines over collections.',       1, 'INTERMEDIATE', 70,  0.00300, true, now()),
  (6, 'concurrency',   'Concurrency',                  'Threads, executors, virtual threads.',         1, 'ADVANCED',     120, 0.00500, true, now()),
  (7, 'spring-boot',   'Spring Boot',                  'Dependency injection and auto-configuration.', 1, 'ADVANCED',     150, 0.00300, true, now()),
  (8, 'microservices', 'Microservices',                'Service boundaries, gateways, resilience.',    1, 'EXPERT',       180, 0.00400, true, now())
ON CONFLICT (id) DO NOTHING;

SELECT setval('skills_id_seq', GREATEST((SELECT id FROM skills ORDER BY id DESC LIMIT 1), 1));

-- Ten edges: OOP gates Collections and Generics; Streams needs Collections
-- (hard) and is helped by Generics (soft, strength 0.5); Concurrency needs
-- Collections; Spring Boot needs OOP (hard) and is helped by Collections
-- (soft); Microservices needs both Concurrency and Spring Boot, the latter
-- as the hard gate.
INSERT INTO skill_prerequisites (id, skill_id, prerequisite_skill_id, strength, created_at) VALUES
  (1,  2, 1, 1.00, '2026-09-14 06:10:53.341659+00'),
  (2,  7, 2, 1.00, '2026-09-14 06:10:53.341659+00'),
  (3,  4, 2, 1.00, '2026-09-14 06:10:53.341659+00'),
  (4,  3, 2, 1.00, '2026-09-14 06:10:53.341659+00'),
  (5,  7, 3, 0.50, '2026-09-14 06:10:53.341659+00'),
  (6,  6, 3, 1.00, '2026-09-14 06:10:53.341659+00'),
  (7,  5, 3, 1.00, '2026-09-14 06:10:53.341659+00'),
  (8,  5, 4, 0.50, '2026-09-14 06:10:53.341659+00'),
  (9,  8, 6, 0.50, '2026-09-14 06:10:53.341659+00'),
  (10, 8, 7, 1.00, '2026-09-14 06:10:53.341659+00')
ON CONFLICT (id) DO NOTHING;

SELECT setval('skill_prerequisites_id_seq', GREATEST((SELECT id FROM skill_prerequisites ORDER BY id DESC LIMIT 1), 1));
