-- =============================================================================
-- V906 — Career roles and their skill requirements
--
-- Two roles against the same eight skills the item bank already covers, so
-- gap analysis and path generation have real numbers to work with the moment
-- this ships — no separate seeding pass needed to make the demo honest.
-- Collections is already near-mastered from the diagnostic runs earlier this
-- project, which is deliberate: the gap view should show a mix of met and
-- unmet requirements out of the box, not a wall of zeros.
-- =============================================================================

INSERT INTO career_roles (title, slug, description, category, seniority, icon, is_active, created_at)
VALUES
  ('Backend Java Developer', 'backend-java-developer',
   'Builds and maintains server-side services in Java and Spring Boot.',
   'Software Engineering', 'JUNIOR', 'server', true, now()),
  ('Senior Backend Engineer', 'senior-backend-engineer',
   'Owns service architecture and mentors on the backend Java stack.',
   'Software Engineering', 'SENIOR', 'server-cog', true, now())
ON CONFLICT (slug) DO NOTHING;

INSERT INTO role_skill_requirements (career_role_id, skill_id, required_mastery, weight, is_core, created_at)
SELECT r.id, s.id, req.required_mastery, req.weight, req.is_core, now()
FROM (VALUES
  ('backend-java-developer', 'java-syntax',   0.70, 1.00, true),
  ('backend-java-developer', 'oop',           0.70, 1.00, true),
  ('backend-java-developer', 'collections',   0.75, 1.00, true),
  ('backend-java-developer', 'generics',      0.60, 0.70, false),
  ('backend-java-developer', 'streams',       0.65, 0.80, false),
  ('backend-java-developer', 'concurrency',   0.60, 0.90, true),
  ('backend-java-developer', 'spring-boot',   0.70, 1.00, true),
  ('backend-java-developer', 'microservices', 0.50, 0.60, false),

  ('senior-backend-engineer', 'java-syntax',   0.85, 1.00, true),
  ('senior-backend-engineer', 'oop',           0.85, 1.00, true),
  ('senior-backend-engineer', 'collections',   0.85, 1.00, true),
  ('senior-backend-engineer', 'generics',      0.80, 0.90, true),
  ('senior-backend-engineer', 'streams',       0.80, 0.90, false),
  ('senior-backend-engineer', 'concurrency',   0.85, 1.00, true),
  ('senior-backend-engineer', 'spring-boot',   0.85, 1.00, true),
  ('senior-backend-engineer', 'microservices', 0.75, 1.00, true)
) AS req(role_slug, skill_slug, required_mastery, weight, is_core)
JOIN career_roles r ON r.slug = req.role_slug
JOIN skills s ON s.slug = req.skill_slug
ON CONFLICT DO NOTHING;
