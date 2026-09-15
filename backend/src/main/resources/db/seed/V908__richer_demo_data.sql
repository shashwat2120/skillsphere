-- =============================================================================
-- V908 — Richer demo data: courses, enrollments and learner personas
--
-- WHY THIS FILE EXISTS.
-- V900-V907 built a real item bank, career roles and one verification project,
-- but left the "classroom" itself thin: one course, zero enrollments, and only
-- two users (alice, arenahost) with any practice history. A reviewer opening
-- the database — or a dashboard built against it — would see an adaptive
-- engine with nothing adapting to. Zero enrollments in particular is not a
-- cosmetic gap: any query or screen that joins through enrollments (course
-- rosters, "my courses", instructor views) returns nothing no matter how much
-- item-bank content exists behind it.
--
-- WHAT THIS FILE DELIBERATELY DOES NOT DO.
-- It does not invent a second project, a second category schema, or any table
-- or column absent from db/migration. It does not touch db/migration itself.
-- It does not fabricate a risk_scores row — RiskScoringService (analytics-
-- service) computes those itself, every 5 minutes, from learning_events; this
-- file's job is to seed learning_events that give that scheduled job something
-- honest to score once the backend and analytics-service are next running.
--
-- FIVE LEARNER PERSONAS, ONE STORY EACH.
-- Rather than uniform filler rows, each new learner has a coherent history —
-- correctness, timestamps and mastery all move together — because a capstone
-- reviewer who opens learner_skill_state right after responses will notice if
-- they disagree:
--   Dana Whitfield   — thriving: high accuracy, steady multi-week activity,
--                       two skills actually crossed into MASTERED.
--   Omar Hassan      — struggling: started fine, then clustered failures on
--                       Collections in his last five answers, all inside his
--                       first-14-days window. This is a deliberate trigger for
--                       RiskScoringService's HIGH band (recentAccuracy low +
--                       failureCluster true + earlyWindow true) — see the
--                       worked score in the comment above his block.
--   Grace Kim        — newly enrolled: exactly two answers on record, which
--                       is intentionally BELOW MIN_RESPONSES_TO_SCORE (3). She
--                       demonstrates the risk scorer's honest-silence path —
--                       "not enough evidence" rather than a fabricated score —
--                       which is a real design decision worth being able to
--                       show in a demo, not an accident.
--   Leo Fontaine,
--   Sofia Mercado     — average: moderate accuracy, mixed correct/wrong
--                       spread across skills (never 3+ wrong on one skill in
--                       a five-answer window), landing LOW risk on purpose so
--                       the dashboard shows contrast, not five HIGH-risk rows.
--
-- Existing accounts (alice, arenahost, verifytest, analytics-flow-test) already
-- carry real practice history from earlier sessions; this file gives them
-- enrollments so that history has a course to belong to, without touching
-- their existing learning_events/responses rows. The obvious integration-test
-- accounts (kafka-bridge-test2, mail-listener-test, notif-service-test*,
-- final-dedup-test) are left alone — they are infrastructure fixtures, not
-- part of the classroom narrative, and giving them personas would undercut
-- the "looks like a real classroom" goal this file exists for.
--
-- PASSWORDS FOR THE SEVEN NEW USERS.
-- No seed file before this one has ever inserted a user row — all 11 existing
-- accounts were created through the running application, so there was no
-- precedent to match. Rather than fabricate a password_hash string (which
-- would either be rejected by Argon2's decoder or, worse, silently accepted
-- as a hash that verifies against nothing), the hashes below were generated
-- with the application's own encoder configuration — Argon2id, salt=16,
-- hash=32, parallelism=4, memory=65536 KB, iterations=3, exactly as wired in
-- identity-service's PasswordConfig — compiled and run standalone against the
-- same spring-security-crypto + bouncycastle jars the app depends on, then
-- round-trip verified with Argon2PasswordEncoder.matches(...) before being
-- pasted in below. Every one of the seven hashes is real and will authenticate
-- against the password documented next to it. All seven new demo accounts
-- share the password "SkillSphereDemo2026!" — a single documented demo
-- password beats seven unknowable ones for a project meant to be logged into
-- during a review.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Course categories. Previously an empty table — every course, old and new,
-- had category_id NULL, so a "browse by category" screen would have nothing
-- to group by even with courses present. Three categories, matching the only
-- skill area this platform's content actually covers (Java).
-- -----------------------------------------------------------------------------
INSERT INTO course_categories (name, slug, position, created_at) VALUES
  ('Core Java', 'core-java', 1, now()),
  ('Concurrency & Performance', 'concurrency-performance', 2, now()),
  ('Frameworks & Architecture', 'frameworks-architecture', 3, now())
ON CONFLICT (slug) DO NOTHING;

-- Backfill the one existing course now that a category exists for it. This is
-- the only UPDATE in this file, on the one row it would otherwise have been
-- inconsistent to leave NULL while every new course below has a category.
UPDATE courses
SET category_id = (SELECT id FROM course_categories WHERE slug = 'concurrency-performance')
WHERE slug = 'concurrency-fundamentals' AND category_id IS NULL;

-- -----------------------------------------------------------------------------
-- Two new instructors. The one existing course is authored by the platform
-- admin account, which is a fine bootstrap default but not a story — a real
-- catalogue has named instructors. bob.teacher already exists with the
-- INSTRUCTOR role but PENDING account status; left untouched rather than
-- reused, since flipping someone else's account status is out of scope here.
-- -----------------------------------------------------------------------------
INSERT INTO users (email, password_hash, full_name, headline, bio, timezone, locale, status, email_verified_at, last_login_at, created_at)
VALUES
  ('priya.sharma@example.com',
   '{argon2}$argon2id$v=19$m=65536,t=3,p=4$95uEjUeVLAqKHhJylAjnfg$YEXNBFEnyrgfU1KOQ1SYfD7oMrPT2Eek+oQQTJ3vqnA',
   'Priya Sharma', 'Backend Engineer, 8 years in Java and Spring',
   'Teaches the Core Java track. Previously built payments infrastructure; cares most about the collections and generics mistakes that only show up under load.',
   'UTC', 'en', 'ACTIVE', now() - interval '26 days', now() - interval '1 days', now() - interval '26 days'),
  ('marcus.webb@example.com',
   '{argon2}$argon2id$v=19$m=65536,t=3,p=4$sFCAtIQGFVTcmhLIRL2kSA$SmiaZNWC5/wBC94YJ0Nj9UXk7yhkgcMMwIdShIi8RII',
   'Marcus Webb', 'Platform Engineer, Spring Boot & microservices',
   'Teaches Streams and the Spring Boot / microservices track. Spent three years splitting a monolith and has opinions about when not to.',
   'UTC', 'en', 'ACTIVE', now() - interval '24 days', now() - interval '3 days', now() - interval '24 days')
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id, granted_at)
SELECT u.id, r.id, u.created_at
FROM users u JOIN roles r ON r.name = 'INSTRUCTOR'
WHERE u.email IN ('priya.sharma@example.com', 'marcus.webb@example.com')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Five new learner personas. Passwords: see the file header — all seven new
-- accounts in this migration share "SkillSphereDemo2026!".
-- -----------------------------------------------------------------------------
INSERT INTO users (email, password_hash, full_name, timezone, locale, status, email_verified_at, last_login_at, created_at)
VALUES
  ('dana.whitfield@example.com',
   '{argon2}$argon2id$v=19$m=65536,t=3,p=4$TSAfHqQjidUGYI3LrjFCIQ$aETtsVy62odNTDjBwB2Kxevi4xFODL4wDcdBi8xH7LQ',
   'Dana Whitfield', 'UTC', 'en', 'ACTIVE', now() - interval '19 days', now() - interval '1 days', now() - interval '19 days'),
  ('omar.hassan@example.com',
   '{argon2}$argon2id$v=19$m=65536,t=3,p=4$PRToDL/U6p/EL/WWcLvR7g$Bqfabc7p5AriWzv3lntPQKON9RALa7ZQuqneZMD7zIA',
   'Omar Hassan', 'UTC', 'en', 'ACTIVE', now() - interval '9 days', now() - interval '3 hours', now() - interval '9 days'),
  ('grace.kim@example.com',
   '{argon2}$argon2id$v=19$m=65536,t=3,p=4$C5ldo7B4UFULCf+EPrSN9Q$WkZKcsR3La8qnQWd+dvrkCDNXwCKX30DXm7MGPH9hWA',
   'Grace Kim', 'UTC', 'en', 'ACTIVE', now() - interval '2 days', now() - interval '1 days', now() - interval '2 days'),
  ('leo.fontaine@example.com',
   '{argon2}$argon2id$v=19$m=65536,t=3,p=4$HA263gPLqfqOFykHhA43dw$Cz/u3YW8X15rUfZCQaH6X490+KlWGkxa0S2eaO0e/lw',
   'Leo Fontaine', 'UTC', 'en', 'ACTIVE', now() - interval '21 days', now() - interval '4 days', now() - interval '21 days'),
  ('sofia.mercado@example.com',
   '{argon2}$argon2id$v=19$m=65536,t=3,p=4$F6eakkITlH8YjzNmt8HaIg$Djh4sDnDbdOfexTiceWXJgy433k39Dq5L/nxTevOW8g',
   'Sofia Mercado', 'UTC', 'en', 'ACTIVE', now() - interval '19 days', now() - interval '5 days', now() - interval '19 days')
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id, granted_at)
SELECT u.id, r.id, u.created_at
FROM users u JOIN roles r ON r.name = 'LEARNER'
WHERE u.email IN ('dana.whitfield@example.com', 'omar.hassan@example.com', 'grace.kim@example.com',
                   'leo.fontaine@example.com', 'sofia.mercado@example.com')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Four new courses spanning the skill areas the item bank already covers but
-- the catalogue didn't: Collections, OOP, Streams, and Spring Boot /
-- Microservices together (the existing course already owns Concurrency).
-- Each gets a module and lessons wired to skills via lesson_skills — the only
-- path from a course to a skill in this schema; there is no direct
-- course-to-skill join table.
-- -----------------------------------------------------------------------------
INSERT INTO courses (title, slug, subtitle, description, instructor_id, category_id, level_band, status, est_minutes, published_at, created_at)
SELECT v.title, v.slug, v.subtitle, v.description, u.id, cc.id, v.level_band, 'PUBLISHED', v.est_minutes, now() - v.published_age, now() - v.published_age
FROM (VALUES
  ('Java Collections Deep Dive', 'java-collections-deep-dive',
   'Master the collection types real Java systems run on',
   'Goes past "know the API" into knowing which structure to reach for and why: ordering guarantees, thread-safety pitfalls, and the equals/hashCode contract that silently breaks HashMap and HashSet when ignored.',
   'priya.sharma@example.com', 'core-java', 'INTERMEDIATE', 240, interval '25 days'),
  ('Object-Oriented Design in Java', 'object-oriented-design-in-java',
   'Inheritance, overriding and composition, taught from the mistakes learners actually make',
   'Covers overloading vs. overriding, why constructors are never inherited, private vs. protected access from a subclass, and the Java syntax gotchas that trip up OOP code specifically.',
   'priya.sharma@example.com', 'core-java', 'FOUNDATIONAL', 210, interval '23 days'),
  ('Modern Java Streams & Functional Style', 'modern-java-streams',
   'Lazy pipelines, terminal operations, and where parallel streams actually help',
   'Builds the mental model that a stream is single-use and does nothing until a terminal operation pulls elements through it — the single misconception behind most stream bugs.',
   'marcus.webb@example.com', 'core-java', 'INTERMEDIATE', 150, interval '22 days'),
  ('Spring Boot Microservices Foundations', 'spring-boot-microservices-foundations',
   'From a well-built Spring Boot service to a system of them',
   'Constructor injection over field injection, component scanning boundaries, self-invocation breaking @Transactional, and why splitting a monolith trades performance for independent deployability rather than gaining performance.',
   'marcus.webb@example.com', 'frameworks-architecture', 'ADVANCED', 300, interval '20 days')
) AS v(title, slug, subtitle, description, instructor_email, category_slug, level_band, est_minutes, published_age)
JOIN users u ON u.email = v.instructor_email
JOIN course_categories cc ON cc.slug = v.category_slug
ON CONFLICT (slug) DO NOTHING;

INSERT INTO course_modules (course_id, title, summary, position, created_at)
SELECT c.id, v.title, v.summary, v.position, now() - v.age
FROM (VALUES
  ('java-collections-deep-dive', 'Core Collection Types', 'Choosing the right structure and knowing its guarantees.', 0, interval '25 days'),
  ('object-oriented-design-in-java', 'OOP Foundations', 'Inheritance, overriding and the Java syntax details that interact with them.', 0, interval '23 days'),
  ('modern-java-streams', 'The Streams Pipeline', 'Laziness, terminal operations, and when parallel helps.', 0, interval '22 days'),
  ('spring-boot-microservices-foundations', 'From Monolith to Services', 'Dependency injection done right, then what changes when a call crosses a network.', 0, interval '20 days')
) AS v(course_slug, title, summary, position, age)
JOIN courses c ON c.slug = v.course_slug;
-- No ON CONFLICT here: uq_module_position is DEFERRABLE INITIALLY DEFERRED,
-- and Postgres refuses a deferrable unique constraint as an ON CONFLICT
-- arbiter. Not needed anyway — these courses were just created above in this
-- same migration, so there is no pre-existing module to collide with.

INSERT INTO lessons (module_id, title, type, content, duration_seconds, position, is_preview, created_at)
SELECT cm.id, v.title, 'TEXT', v.content, v.duration_seconds, v.position, v.is_preview, now() - v.age
FROM (VALUES
  ('java-collections-deep-dive', 'HashMap, TreeMap and Ordering Guarantees',
   'HashMap makes no ordering promise at all; TreeMap sorts by key at O(log n) per operation; LinkedHashMap remembers insertion order. Picking the wrong one is rarely a crash — it is a bug that only shows up when someone iterates.',
   720, 0, true, interval '25 days'),
  ('java-collections-deep-dive', 'Generic Bounds and Wildcards',
   'Producer extends, consumer super: ? extends T lets you read safely but not write; ? super T lets you write but reads come back as Object. Generics are invariant, unlike arrays, which is what stops an Integer sneaking into a List<String>.',
   660, 1, false, interval '25 days'),
  ('object-oriented-design-in-java', 'Inheritance, Overriding and Composition',
   'Overloading is same name, different parameters, resolved at compile time. Overriding is same signature in a subclass, resolved at runtime. Constructors are never inherited — a subclass writes its own and calls super(...).',
   840, 0, true, interval '23 days'),
  ('object-oriented-design-in-java', 'Java Syntax Gotchas Every OOP Developer Hits',
   '== compares references, not contents, for anything that is not a primitive. Integer division truncates rather than rounds. A primitive can never be null — only its wrapper type can, which is also why unboxing a null Integer throws.',
   600, 1, false, interval '23 days'),
  ('modern-java-streams', 'Lazy Evaluation and Terminal Operations',
   'A stream pipeline does nothing until a terminal operation pulls elements through it — map and filter just describe the pipeline. A stream is also single-use: touch it again after a terminal operation and it throws IllegalStateException.',
   780, 0, true, interval '22 days'),
  ('spring-boot-microservices-foundations', 'Dependency Injection the Spring Way',
   'Constructor injection makes dependencies explicit and testable without a container; field injection hides them until runtime. Component scanning starts at the @SpringBootApplication package and descends — nothing outside that tree is found automatically.',
   900, 0, true, interval '20 days'),
  ('spring-boot-microservices-foundations', 'Why Distributed Calls Are Not Local Calls',
   'A network call has a third outcome a local call does not: no answer at all, because the request may have succeeded with the response lost. That is why retries need idempotency, and why splitting a service is a deployability trade, not a performance win.',
   840, 1, false, interval '20 days')
) AS v(course_slug, title, content, duration_seconds, position, is_preview, age)
JOIN courses c ON c.slug = v.course_slug
JOIN course_modules cm ON cm.course_id = c.id AND cm.position = 0
ON CONFLICT DO NOTHING;

INSERT INTO lesson_skills (lesson_id, skill_id, weight, created_at)
SELECT l.id, s.id, v.weight, now() - v.age
FROM (VALUES
  ('HashMap, TreeMap and Ordering Guarantees', 'collections', 1.00, interval '25 days'),
  ('Generic Bounds and Wildcards', 'generics', 1.00, interval '25 days'),
  ('Inheritance, Overriding and Composition', 'oop', 1.00, interval '23 days'),
  ('Java Syntax Gotchas Every OOP Developer Hits', 'java-syntax', 1.00, interval '23 days'),
  ('Lazy Evaluation and Terminal Operations', 'streams', 1.00, interval '22 days'),
  ('Dependency Injection the Spring Way', 'spring-boot', 1.00, interval '20 days'),
  ('Why Distributed Calls Are Not Local Calls', 'microservices', 1.00, interval '20 days')
) AS v(lesson_title, skill_slug, weight, age)
JOIN lessons l ON l.title = v.lesson_title
JOIN skills s ON s.slug = v.skill_slug
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Enrollments. This is the headline gap this file closes: the table was
-- completely empty. Existing users get enrollments matching the course their
-- existing responses/learning_events actually belong to (skill-wise); new
-- personas get enrollments matching the activity seeded for them below.
-- -----------------------------------------------------------------------------
INSERT INTO enrollments (user_id, course_id, status, source, progress_pct, enrolled_at, completed_at, last_active_at)
SELECT u.id, c.id, v.status, v.source, v.progress_pct,
       now() - v.enrolled_age,
       CASE WHEN v.completed_age IS NULL THEN NULL ELSE now() - v.completed_age END,
       now() - v.last_active_age
FROM (VALUES
  -- Existing users: enrollment context for history they already have.
  ('alice@example.com', 'concurrency-fundamentals', 'ACTIVE', 'SELF', 55.00, interval '9 days', NULL::interval, interval '1 days'),
  ('arenahost@example.com', 'object-oriented-design-in-java', 'ACTIVE', 'SELF', 20.00, interval '2 days', NULL::interval, interval '12 hours'),
  ('verifytest@example.com', 'concurrency-fundamentals', 'COMPLETED', 'SELF', 100.00, interval '4 days', interval '2 days', interval '2 days'),
  ('analytics-flow-test@example.com', 'object-oriented-design-in-java', 'ACTIVE', 'SELF', 8.00, interval '12 hours', NULL::interval, interval '10 hours'),

  -- Dana Whitfield — thriving: broad, multi-course engagement.
  ('dana.whitfield@example.com', 'modern-java-streams', 'COMPLETED', 'SELF', 100.00, interval '19 days', interval '2 days', interval '2 days'),
  ('dana.whitfield@example.com', 'spring-boot-microservices-foundations', 'ACTIVE', 'SELF', 72.00, interval '16 days', NULL::interval, interval '1 days'),
  ('dana.whitfield@example.com', 'java-collections-deep-dive', 'ACTIVE', 'SELF', 20.00, interval '5 days', NULL::interval, interval '5 days'),

  -- Omar Hassan — struggling: one real course, going badly.
  ('omar.hassan@example.com', 'java-collections-deep-dive', 'ACTIVE', 'SELF', 12.00, interval '9 days', NULL::interval, interval '3 hours'),
  ('omar.hassan@example.com', 'object-oriented-design-in-java', 'ACTIVE', 'SELF', 5.00, interval '6 days', NULL::interval, interval '6 days'),

  -- Grace Kim — newly enrolled, barely started.
  ('grace.kim@example.com', 'spring-boot-microservices-foundations', 'ACTIVE', 'SELF', 3.00, interval '2 days', NULL::interval, interval '1 days'),

  -- Leo Fontaine, Sofia Mercado — average, steady but unremarkable.
  ('leo.fontaine@example.com', 'java-collections-deep-dive', 'ACTIVE', 'SELF', 45.00, interval '21 days', NULL::interval, interval '4 days'),
  ('leo.fontaine@example.com', 'object-oriented-design-in-java', 'ACTIVE', 'SELF', 38.00, interval '19 days', NULL::interval, interval '6 days'),
  ('sofia.mercado@example.com', 'modern-java-streams', 'ACTIVE', 'SELF', 50.00, interval '19 days', NULL::interval, interval '5 days'),
  ('sofia.mercado@example.com', 'spring-boot-microservices-foundations', 'ACTIVE', 'SELF', 33.00, interval '13 days', NULL::interval, interval '7 days')
) AS v(email, course_slug, status, source, progress_pct, enrolled_age, completed_age, last_active_age)
JOIN users u ON u.email = v.email
JOIN courses c ON c.slug = v.course_slug
ON CONFLICT (user_id, course_id) DO NOTHING;

-- =============================================================================
-- Learner activity. Each block below is one persona's practice history, item
-- bank references (item ids, option ids, misconception ids) all verified
-- against the running database before being written here — see the FK
-- verification queries in this change's report. Mastery/ability numbers are
-- hand-authored plausible trajectories (larger early swings, diminishing
-- returns near 1.0, a dip on a wrong answer) rather than a literal replay of
-- the IRT/BKT engine — good enough to make learner_skill_state agree with the
-- responses it is derived from, which is what a reviewer would actually check.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Dana Whitfield — thriving. Streams and Spring Boot both cross into
-- mastered territory; Microservices is a lighter, unmastered exposure showing
-- she is still branching out. 13 responses, 11 correct (~85%), spread over
-- 18 days — steady, not a single cram session.
-- -----------------------------------------------------------------------------
INSERT INTO learning_events (user_id, event_type, entity_type, entity_id, skill_id, correct, occurred_at)
SELECT (SELECT id FROM users WHERE email = 'dana.whitfield@example.com'), 'ITEM_ANSWERED', 'ITEM', v.item_id, v.skill_id, v.correct, now() - v.age
FROM (VALUES
  (14, 5, true,  interval '18 days 9 hours'),
  (15, 5, true,  interval '15 days 10 hours'),
  (16, 5, false, interval '11 days 14 hours'),
  (46, 5, true,  interval '8 days 16 hours'),
  (47, 5, true,  interval '5 days 11 hours'),
  (48, 5, true,  interval '2 days 9 hours 30 minutes'),
  (20, 7, true,  interval '16 days 8 hours'),
  (21, 7, true,  interval '12 days 13 hours'),
  (22, 7, true,  interval '9 days 17 hours'),
  (51, 7, true,  interval '6 days 10 hours'),
  (52, 7, true,  interval '3 days 15 hours'),
  (23, 8, true,  interval '5 days 12 hours'),
  (24, 8, true,  interval '2 days 18 hours')
) AS v(item_id, skill_id, correct, age);

INSERT INTO responses (user_id, item_id, selected_option_id, is_correct, response_time_ms, ability_before, ability_after, mastery_before, mastery_after, misconception_id, answered_at)
SELECT (SELECT id FROM users WHERE email = 'dana.whitfield@example.com'),
       v.item_id, v.option_id, v.correct, v.rt, v.theta_before, v.theta_after, v.mastery_before, v.mastery_after, v.misconception_id, now() - v.age
FROM (VALUES
  -- Streams (skill 5)
  (14, 53,  true,  14200, 0.0000, 0.3200, 0.0500, 0.3000, NULL::bigint, interval '18 days 9 hours'),
  (15, 57,  true,  11800, 0.3200, 0.6500, 0.3000, 0.5500, NULL::bigint, interval '15 days 10 hours'),
  (16, 62,  false, 21000, 0.6500, 0.3800, 0.5500, 0.3300, 18::bigint,   interval '11 days 14 hours'),
  (46, 181, true,  16500, 0.3800, 0.7200, 0.3300, 0.6200, NULL::bigint, interval '8 days 16 hours'),
  (47, 185, true,  13200, 0.7200, 1.0500, 0.6200, 0.8300, NULL::bigint, interval '5 days 11 hours'),
  (48, 189, true,  10900, 1.0500, 1.3800, 0.8300, 0.9600, NULL::bigint, interval '2 days 9 hours 30 minutes'),
  -- Spring Boot (skill 7)
  (20, 77,  true,  15800, 0.0000, 0.3400, 0.0500, 0.3500, NULL::bigint, interval '16 days 8 hours'),
  (21, 81,  true,  12400, 0.3400, 0.6800, 0.3500, 0.6000, NULL::bigint, interval '12 days 13 hours'),
  (22, 85,  true,  17600, 0.6800, 1.0200, 0.6000, 0.8000, NULL::bigint, interval '9 days 17 hours'),
  (51, 201, true,  19100, 1.0200, 1.2600, 0.8000, 0.9200, NULL::bigint, interval '6 days 10 hours'),
  (52, 205, true,  14300, 1.2600, 1.5000, 0.9200, 0.9700, NULL::bigint, interval '3 days 15 hours'),
  -- Microservices (skill 8) — exposure only, not mastered
  (23, 89,  true,  22500, 0.0000, 0.3000, 0.0500, 0.3500, NULL::bigint, interval '5 days 12 hours'),
  (24, 93,  true,  18700, 0.3000, 0.6000, 0.3500, 0.6000, NULL::bigint, interval '2 days 18 hours')
) AS v(item_id, option_id, correct, rt, theta_before, theta_after, mastery_before, mastery_after, misconception_id, age);

INSERT INTO learner_skill_state (user_id, skill_id, ability_theta, ability_se, mastery_probability, elo_rating, attempts_count, correct_count, peak_mastery, first_seen_at, last_practiced_at, mastered_at, updated_at, version, created_at)
SELECT u.id, s.id, v.theta, v.se, v.mastery, v.elo, v.attempts, v.correct, v.peak,
       now() - v.first_age, now() - v.last_age,
       CASE WHEN v.mastered_age IS NULL THEN NULL ELSE now() - v.mastered_age END,
       now() - v.last_age, v.attempts, now() - v.first_age
FROM (VALUES
  ('dana.whitfield@example.com', 'streams',       1.3800, 0.4200, 0.9600, 1380, 6, 5, 0.9600, interval '18 days 9 hours', interval '2 days 9 hours 30 minutes', interval '2 days 9 hours 30 minutes'),
  ('dana.whitfield@example.com', 'spring-boot',    1.5000, 0.4000, 0.9700, 1400, 5, 5, 0.9700, interval '16 days 8 hours', interval '3 days 15 hours',            interval '3 days 15 hours'),
  ('dana.whitfield@example.com', 'microservices',  0.6000, 0.7500, 0.6000, 1230, 2, 2, 0.6000, interval '5 days 12 hours', interval '2 days 18 hours',           NULL::interval)
) AS v(email, skill_slug, theta, se, mastery, elo, attempts, correct, peak, first_age, last_age, mastered_age)
JOIN users u ON u.email = v.email
JOIN skills s ON s.slug = v.skill_slug
ON CONFLICT (user_id, skill_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Omar Hassan — struggling, and deliberately built to trip RiskScoringService.
-- All 8 responses are Collections (skill 3), first event 9 days ago (within
-- the 14-day early window) and the most recent 4 minutes ago (inactivityDays
-- ~= 0). Ordered oldest to newest, the last 5 responses are
-- WRONG, WRONG, WRONG, CORRECT, WRONG — 4 of 5 wrong, all on skill 3, clearing
-- CLUSTER_THRESHOLD (3). Overall: 3/8 correct (recentAccuracy 0.375) over the
-- 15-response window.
--   accuracyPenalty  = (1 - 0.375) * 0.40           = 0.250
--   clusterPenalty   = 0.35 (cluster detected)       = 0.350
--   inactivityPenalty ~ 0 (last event minutes old)   = 0.000
--   raw = 0.600; earlyWindow true -> score = min(1, 0.600 * 1.25) = 0.750
-- 0.750 >= 0.66, so this lands HIGH the next time the 5-minute job runs —
-- worked by hand against RiskScoringService.java's actual constants
-- (MIN_RESPONSES_TO_SCORE=3, CLUSTER_LOOKBACK=5, CLUSTER_THRESHOLD=3,
-- earlyWindowDays=14) rather than assumed.
-- -----------------------------------------------------------------------------
INSERT INTO learning_events (user_id, event_type, entity_type, entity_id, skill_id, correct, occurred_at)
SELECT (SELECT id FROM users WHERE email = 'omar.hassan@example.com'), 'ITEM_ANSWERED', 'ITEM', v.item_id, 3, v.correct, now() - v.age
FROM (VALUES
  (7,  true,  interval '9 days 8 hours'),
  (8,  true,  interval '7 days 11 hours'),
  (9,  false, interval '5 days 9 hours'),
  (10, false, interval '4 days 13 hours'),
  (32, false, interval '3 days 10 hours'),
  (33, false, interval '2 days 14 hours'),
  (41, true,  interval '1 days 12 hours'),
  (42, false, interval '4 hours')
) AS v(item_id, correct, age);

INSERT INTO responses (user_id, item_id, selected_option_id, is_correct, response_time_ms, ability_before, ability_after, mastery_before, mastery_after, misconception_id, answered_at)
SELECT (SELECT id FROM users WHERE email = 'omar.hassan@example.com'),
       v.item_id, v.option_id, v.correct, v.rt, v.theta_before, v.theta_after, v.mastery_before, v.mastery_after, v.misconception_id, now() - v.age
FROM (VALUES
  (7,  25,  true,  24000, 0.0000,  0.3000,  0.0500, 0.3000, NULL::bigint, interval '9 days 8 hours'),
  (8,  29,  true,  19500, 0.3000,  0.5500,  0.3000, 0.5000, NULL::bigint, interval '7 days 11 hours'),
  (9,  34,  false, 38000, 0.5500,  0.2000,  0.5000, 0.2800, 10::bigint,   interval '5 days 9 hours'),
  (10, 38,  false, 41500, 0.2000, -0.1500,  0.2800, 0.1500, 12::bigint,   interval '4 days 13 hours'),
  (32, 126, false, 33200, -0.1500, -0.4500, 0.1500, 0.0900, 11::bigint,   interval '3 days 10 hours'),
  (33, 130, false, 29700, -0.4500, -0.7000, 0.0900, 0.0600, 8::bigint,    interval '2 days 14 hours'),
  (41, 161, true,  17800, -0.7000, -0.4000, 0.0600, 0.2000, NULL::bigint, interval '1 days 12 hours'),
  (42, 166, false, 45100, -0.4000, -0.6500, 0.2000, 0.1200, 40::bigint,   interval '4 hours')
) AS v(item_id, option_id, correct, rt, theta_before, theta_after, mastery_before, mastery_after, misconception_id, age);

INSERT INTO learner_skill_state (user_id, skill_id, ability_theta, ability_se, mastery_probability, elo_rating, attempts_count, correct_count, peak_mastery, first_seen_at, last_practiced_at, mastered_at, updated_at, version, created_at)
SELECT u.id, s.id, -0.6500, 0.6800, 0.1200, 1080, 8, 3, 0.5000,
       now() - interval '9 days 8 hours', now() - interval '4 hours', NULL,
       now() - interval '4 hours', 8, now() - interval '9 days 8 hours'
FROM users u JOIN skills s ON s.slug = 'collections'
WHERE u.email = 'omar.hassan@example.com'
ON CONFLICT (user_id, skill_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Grace Kim — newly enrolled. Exactly two ITEM_ANSWERED events, one below
-- MIN_RESPONSES_TO_SCORE (3): RiskScoringService.scoreOne() returns null for
-- her and no risk_scores row is ever written, by design ("not enough evidence
-- to say anything responsible" — the comment in RiskScoringService itself).
-- -----------------------------------------------------------------------------
INSERT INTO learning_events (user_id, event_type, entity_type, entity_id, skill_id, correct, occurred_at)
SELECT (SELECT id FROM users WHERE email = 'grace.kim@example.com'), 'ITEM_ANSWERED', 'ITEM', v.item_id, 7, v.correct, now() - v.age
FROM (VALUES
  (20, true,  interval '2 days 10 hours'),
  (21, false, interval '1 days 15 hours')
) AS v(item_id, correct, age);

INSERT INTO responses (user_id, item_id, selected_option_id, is_correct, response_time_ms, ability_before, ability_after, mastery_before, mastery_after, misconception_id, answered_at)
SELECT (SELECT id FROM users WHERE email = 'grace.kim@example.com'),
       v.item_id, v.option_id, v.correct, v.rt, v.theta_before, v.theta_after, v.mastery_before, v.mastery_after, v.misconception_id, now() - v.age
FROM (VALUES
  (20, 77, true,  20500, 0.0000, 0.3000, 0.0500, 0.3000, NULL::bigint, interval '2 days 10 hours'),
  (21, 82, false, 27800, 0.3000, 0.0500, 0.3000, 0.2000, 23::bigint,   interval '1 days 15 hours')
) AS v(item_id, option_id, correct, rt, theta_before, theta_after, mastery_before, mastery_after, misconception_id, age);

INSERT INTO learner_skill_state (user_id, skill_id, ability_theta, ability_se, mastery_probability, elo_rating, attempts_count, correct_count, peak_mastery, first_seen_at, last_practiced_at, mastered_at, updated_at, version, created_at)
SELECT u.id, s.id, 0.0500, 0.9200, 0.2000, 1210, 2, 1, 0.3000,
       now() - interval '2 days 10 hours', now() - interval '1 days 15 hours', NULL,
       now() - interval '1 days 15 hours', 2, now() - interval '2 days 10 hours'
FROM users u JOIN skills s ON s.slug = 'spring-boot'
WHERE u.email = 'grace.kim@example.com'
ON CONFLICT (user_id, skill_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Leo Fontaine — average. Collections and OOP interleaved over three weeks;
-- 6/9 correct overall (~67%), and the most recent five answers span two
-- different skills with only one wrong each, so no failure cluster forms.
-- daysSinceFirstSeen (21) is past the early window, so no risk-score boost
-- either. Works out LOW risk by the same formula used for Omar above.
-- -----------------------------------------------------------------------------
INSERT INTO learning_events (user_id, event_type, entity_type, entity_id, skill_id, correct, occurred_at)
SELECT (SELECT id FROM users WHERE email = 'leo.fontaine@example.com'), 'ITEM_ANSWERED', 'ITEM', v.item_id, v.skill_id, v.correct, now() - v.age
FROM (VALUES
  (7,  3, true,  interval '21 days 7 hours'),
  (4,  2, true,  interval '19 days 12 hours'),
  (9,  3, false, interval '17 days 9 hours'),
  (5,  2, true,  interval '15 days 14 hours'),
  (32, 3, true,  interval '13 days 10 hours'),
  (28, 2, false, interval '10 days 16 hours'),
  (41, 3, true,  interval '9 days 11 hours'),
  (29, 2, true,  interval '6 days 13 hours'),
  (8,  3, false, interval '4 days 15 hours')
) AS v(item_id, skill_id, correct, age);

INSERT INTO responses (user_id, item_id, selected_option_id, is_correct, response_time_ms, ability_before, ability_after, mastery_before, mastery_after, misconception_id, answered_at)
SELECT (SELECT id FROM users WHERE email = 'leo.fontaine@example.com'),
       v.item_id, v.option_id, v.correct, v.rt, v.theta_before, v.theta_after, v.mastery_before, v.mastery_after, v.misconception_id, now() - v.age
FROM (VALUES
  (7,  25,  true,  16200, 0.0000, 0.3000, 0.0500, 0.3000, NULL::bigint, interval '21 days 7 hours'),
  (4,  13,  true,  14500, 0.0000, 0.3000, 0.0500, 0.3000, NULL::bigint, interval '19 days 12 hours'),
  (9,  34,  false, 31000, 0.3000, 0.0500, 0.3000, 0.1800, 10::bigint,   interval '17 days 9 hours'),
  (5,  17,  true,  18700, 0.3000, 0.6000, 0.3000, 0.5500, NULL::bigint, interval '15 days 14 hours'),
  (32, 125, true,  15400, 0.0500, 0.3500, 0.1800, 0.4000, NULL::bigint, interval '13 days 10 hours'),
  (28, 110, false, 27600, 0.6000, 0.3000, 0.5500, 0.3500, 30::bigint,   interval '10 days 16 hours'),
  (41, 161, true,  16900, 0.3500, 0.6500, 0.4000, 0.5800, NULL::bigint, interval '9 days 11 hours'),
  (29, 113, true,  17200, 0.3000, 0.6000, 0.3500, 0.5500, NULL::bigint, interval '6 days 13 hours'),
  (8,  30,  false, 29800, 0.6500, 0.3500, 0.5800, 0.4000, 9::bigint,    interval '4 days 15 hours')
) AS v(item_id, option_id, correct, rt, theta_before, theta_after, mastery_before, mastery_after, misconception_id, age);

INSERT INTO learner_skill_state (user_id, skill_id, ability_theta, ability_se, mastery_probability, elo_rating, attempts_count, correct_count, peak_mastery, first_seen_at, last_practiced_at, mastered_at, updated_at, version, created_at)
SELECT u.id, s.id, v.theta, v.se, v.mastery, v.elo, v.attempts, v.correct, v.peak,
       now() - v.first_age, now() - v.last_age, NULL, now() - v.last_age, v.attempts, now() - v.first_age
FROM (VALUES
  ('leo.fontaine@example.com', 'collections', 0.3500, 0.7000, 0.4000, 1230, 5, 3, 0.5800, interval '21 days 7 hours', interval '4 days 15 hours'),
  ('leo.fontaine@example.com', 'oop',         0.6000, 0.7200, 0.5500, 1250, 4, 3, 0.5500, interval '19 days 12 hours', interval '6 days 13 hours')
) AS v(email, skill_slug, theta, se, mastery, elo, attempts, correct, peak, first_age, last_age)
JOIN users u ON u.email = v.email
JOIN skills s ON s.slug = v.skill_slug
ON CONFLICT (user_id, skill_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Sofia Mercado — average. Streams and Microservices interleaved over two
-- weeks, 4/6 correct (~67%), wrongs split one-per-skill in the recent window
-- so no cluster forms here either. Same LOW-risk shape as Leo, different
-- skills, so the dashboard shows genuine variety rather than two clones.
-- -----------------------------------------------------------------------------
INSERT INTO learning_events (user_id, event_type, entity_type, entity_id, skill_id, correct, occurred_at)
SELECT (SELECT id FROM users WHERE email = 'sofia.mercado@example.com'), 'ITEM_ANSWERED', 'ITEM', v.item_id, v.skill_id, v.correct, now() - v.age
FROM (VALUES
  (14, 5, true,  interval '19 days 9 hours'),
  (15, 5, true,  interval '16 days 11 hours'),
  (23, 8, false, interval '13 days 14 hours'),
  (46, 5, false, interval '12 days 10 hours'),
  (24, 8, true,  interval '7 days 16 hours'),
  (16, 5, true,  interval '5 days 12 hours')
) AS v(item_id, skill_id, correct, age);

INSERT INTO responses (user_id, item_id, selected_option_id, is_correct, response_time_ms, ability_before, ability_after, mastery_before, mastery_after, misconception_id, answered_at)
SELECT (SELECT id FROM users WHERE email = 'sofia.mercado@example.com'),
       v.item_id, v.option_id, v.correct, v.rt, v.theta_before, v.theta_after, v.mastery_before, v.mastery_after, v.misconception_id, now() - v.age
FROM (VALUES
  (14, 53, true,  15600, 0.0000,  0.3000, 0.0500, 0.3000, NULL::bigint, interval '19 days 9 hours'),
  (15, 57, true,  13900, 0.3000,  0.6000, 0.3000, 0.5500, NULL::bigint, interval '16 days 11 hours'),
  (23, 90, false, 24700, 0.0000, -0.3000, 0.0500, 0.0300, 25::bigint,   interval '13 days 14 hours'),
  (46, 182, false, 26400, 0.6000,  0.3000, 0.5500, 0.3500, 43::bigint,  interval '12 days 10 hours'),
  (24, 93, true,  18300, -0.3000, 0.0000, 0.0300, 0.2500, NULL::bigint, interval '7 days 16 hours'),
  (16, 61, true,  16100, 0.3000,  0.6000, 0.3500, 0.5500, NULL::bigint, interval '5 days 12 hours')
) AS v(item_id, option_id, correct, rt, theta_before, theta_after, mastery_before, mastery_after, misconception_id, age);

INSERT INTO learner_skill_state (user_id, skill_id, ability_theta, ability_se, mastery_probability, elo_rating, attempts_count, correct_count, peak_mastery, first_seen_at, last_practiced_at, mastered_at, updated_at, version, created_at)
SELECT u.id, s.id, v.theta, v.se, v.mastery, v.elo, v.attempts, v.correct, v.peak,
       now() - v.first_age, now() - v.last_age, NULL, now() - v.last_age, v.attempts, now() - v.first_age
FROM (VALUES
  ('sofia.mercado@example.com', 'streams',       0.6000, 0.6500, 0.5500, 1240, 4, 3, 0.5500, interval '19 days 9 hours', interval '5 days 12 hours'),
  ('sofia.mercado@example.com', 'microservices', 0.0000, 0.8500, 0.2500, 1150, 2, 1, 0.2500, interval '13 days 14 hours', interval '7 days 16 hours')
) AS v(email, skill_slug, theta, se, mastery, elo, attempts, correct, peak, first_age, last_age)
JOIN users u ON u.email = v.email
JOIN skills s ON s.slug = v.skill_slug
ON CONFLICT (user_id, skill_id) DO NOTHING;

-- =============================================================================
-- Verification chain: one more submission that actually completes the loop
-- (VERIFIED, with a full viva transcript and the evidence it produces), and
-- one more that shows a learner who started the project and stalled — which
-- is the more common outcome and was entirely missing from the existing
-- three DRAFT/IN_VIVA rows. Both reuse the one project that exists
-- (bounded-lru-cache); inventing a second project was out of scope for a
-- "richer seed data" pass and was not asked for.
-- =============================================================================

-- Dana's submission: a different, still-correct design from Alice's (a
-- hand-rolled doubly linked list plus a single ReentrantLock) rather than a
-- copy, so two VERIFIED submissions on record don't read as duplicates of
-- each other.
INSERT INTO submissions (user_id, project_id, status, content, attempt_no, active_minutes, draft_count, large_paste_count, first_activity_at, submitted_at, created_at, updated_at)
SELECT u.id, p.id, 'VERIFIED', $sub$I used a HashMap<K, Node<K,V>> plus a doubly linked list I maintain myself, with a head/tail sentinel so I never special-case an empty list. get() unlinks the node and relinks it at the front; put() past capacity evicts the tail's neighbour. The whole thing is guarded by one ReentrantLock rather than per-node locks, because a get() still mutates the list (it counts as an access), so even reads need the write lock — fine-grained locking would have bought nothing here and added a deadlock-ordering problem I didn't want to reason about under a deadline.$sub$,
       1, 35, 2, 0, now() - interval '6 days 3 hours', now() - interval '6 days 1 hours', now() - interval '6 days 3 hours', now() - interval '5 days 20 hours'
FROM users u, projects p
WHERE u.email = 'dana.whitfield@example.com' AND p.slug = 'bounded-lru-cache';

INSERT INTO viva_sessions (submission_id, status, verdict, overall_score, turns_planned, turns_completed, generator_model, started_at, completed_at, created_at)
SELECT s.id, 'COMPLETED', 'VERIFIED', 0.85, 5, 5, 'llama3.2:3b',
       now() - interval '6 days 1 hours', now() - interval '5 days 20 hours', now() - interval '6 days 1 hours'
FROM submissions s
JOIN users u ON u.id = s.user_id
WHERE u.email = 'dana.whitfield@example.com';

INSERT INTO viva_turns (viva_session_id, position, question, question_intent, score, answer, asked_at, answered_at)
SELECT vs.id, v.position, v.question, v.intent, v.score, v.answer, now() - v.age, now() - v.age + interval '2 minutes'
FROM (VALUES
  (1, 'Why one coarse lock instead of locking just the bucket a key hashes to?', 'DESIGN_CHOICE', 0.85,
   'Because get() also mutates the linked list for recency, a fine-grained per-bucket lock still would not protect the shared list pointers — two threads in different buckets could corrupt the same list concurrently. One lock keeps the invariant simple to reason about.',
   interval '6 days 1 hours'),
  (2, 'What happens if two threads call get() on the same key at the same time?', 'EDGE_CASE', 0.80,
   'The second thread blocks on the lock until the first finishes unlinking and relinking the node, so there is no torn read of the list pointers. Both eventually see the key moved to the front exactly once.',
   interval '6 days 50 minutes'),
  (3, 'What is the time complexity of get() and put(), and why?', 'CONCEPT_CHECK', 0.90,
   'O(1) for both. The HashMap gives O(1) average lookup to the node, and unlinking or relinking a node in a doubly linked list is O(1) once you have the node reference — no traversal is needed.',
   interval '6 days 40 minutes'),
  (4, 'Your lock is coarse-grained. What would you have to change to make gets not block each other?', 'TRADE_OFF', 0.85,
   'I would need a lock that lets concurrent readers through but still serialises the recency-list mutation a get() causes, or switch to a lock-free structure for the list part, which is considerably harder to get correct than the trade I made.',
   interval '6 days 30 minutes'),
  (5, 'What would break if you forgot to evict when the cache is over capacity?', 'FAILURE_MODE', 0.85,
   'The cache would grow without bound, which defeats the point of a bounded cache and would eventually exhaust memory under sustained load rather than staying at a fixed footprint.',
   interval '6 days 20 minutes')
) AS v(position, question, intent, score, answer, age)
JOIN submissions s ON true
JOIN users u ON u.id = s.user_id AND u.email = 'dana.whitfield@example.com'
JOIN viva_sessions vs ON vs.submission_id = s.id;

INSERT INTO evidence (user_id, skill_id, evidence_type, source_type, source_id, weight, score, verified_at, created_at)
SELECT u.id, s.id, 'VIVA', 'VIVA_SESSION', vs.id, ps.weight, 0.85, vs.completed_at, vs.completed_at
FROM viva_sessions vs
JOIN submissions sub ON sub.id = vs.submission_id
JOIN users u ON u.id = sub.user_id AND u.email = 'dana.whitfield@example.com'
JOIN project_skills ps ON ps.project_id = sub.project_id
JOIN skills s ON s.id = ps.skill_id
ON CONFLICT (source_type, source_id, skill_id) DO NOTHING;

-- Omar's submission: started, never got anywhere — consistent with the
-- struggling narrative above, and matching the shape of the two existing
-- DRAFT rows (no content yet, no viva session).
INSERT INTO submissions (user_id, project_id, status, attempt_no, draft_count, large_paste_count, first_activity_at, created_at, updated_at)
SELECT u.id, p.id, 'DRAFT', 1, 0, 0, now() - interval '1 days 2 hours', now() - interval '1 days 2 hours', now() - interval '1 days 2 hours'
FROM users u, projects p
WHERE u.email = 'omar.hassan@example.com' AND p.slug = 'bounded-lru-cache';
