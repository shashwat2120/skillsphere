-- Test-only seed data, standing in for the reference rows the monolith's
-- Flyway migration (V1__identity.sql) inserts in production. This service has
-- no Flyway of its own yet (see pom.xml and application-test.yml), and
-- ddl-auto: create-drop only builds the schema from the entities — it never
-- runs migration-authored seed data or picks up a column's DB-level DEFAULT,
-- so created_at is supplied explicitly here rather than left to now().
--
-- Roles are reference data in this system, not something any code path ever
-- creates at runtime (see RoleName's own comment) — this file is the only
-- place besides the real migration where the four names are allowed to
-- appear, and the two must be kept in sync by hand.
INSERT INTO roles (name, description, created_at) VALUES
    ('LEARNER',    'Learns through paths, takes assessments, submits projects and defends them', now()),
    ('INSTRUCTOR', 'Authors content and items, reviews submissions, runs live sessions', now()),
    ('ADMIN',      'Manages the skill graph, career catalogue, users and platform settings', now()),
    ('EMPLOYER',   'Views shared skill passports and their supporting evidence', now());
